package jenkins.plugins.fogbugz.notifications;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.Context;

import hudson.EnvVars;
import hudson.tasks.test.AbstractTestResultAction;
import jenkins.plugins.fogbugz.FogbugzProjectProperty;
import lombok.Setter;
import org.apache.commons.lang.StringEscapeUtils;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.*;
import lombok.Getter;
import lombok.extern.java.Log;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.paylogic.fogbugz.FogbugzCase;
import org.paylogic.fogbugz.FogbugzManager;
import org.paylogic.fogbugz.FogbugzEvent;
import org.paylogic.fogbugz.InvalidResponseException;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Notifier in Jenkins system, reports status to Fogbugz.
 * Gets its information from a build parameter called "CASE_ID", so make sure you set that.
 */
@Log
public class FogbugzNotifier extends Notifier {

    @DataBoundConstructor
    public FogbugzNotifier() {
        super();
    }

    public String getDisplayName() {
        return "Fogbugz notification settings";
    }

	public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
	}

	@Override
	public boolean needsToRunAfterFinalized() {
        return true;
	}

    private String stringOrEmpty(int param) {
        return param == 0 ? Integer.toString(param) : "";
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        log.info("Now performing post-build action for Fogbugz reporting.");
        PrintStream l = listener.getLogger();
        l.println("----------------------------------------------------------");
        l.println("----------- Now sending build status to FogBugz ----------");
        l.println("----------------------------------------------------------");

        EnvVars envVars = null;
        try {
            envVars = build.getEnvironment(listener);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception while fetching environment variables.", e);
        }

        String givenCaseId = envVars.get("CASE_ID", "");

        /* Get the name of the branch so we can figure out which case this build belongs to */
        if (!givenCaseId.isEmpty() && !givenCaseId.equals("0")) {
            log.info("Using given case ID for reporting.");
        } else {
            log.info("No case branch found, currently not reporting to fogbugz.");
            return false;  // TODO: should we return true or false here?
                           // TODO: and does that even impact build status since this is a Notifier?
        }

        int usableCaseId = 0;
        if (!givenCaseId.isEmpty()) {
            usableCaseId = Integer.parseInt(givenCaseId);
        }

        /* Parse log file and build message string with its result. */
        LogMessageSearcher messageSearcher = new LogMessageSearcher(build);
        String reportingExtraMessage = "";
        try {
            for (String line: messageSearcher.searchForMessages()) {
                reportingExtraMessage += line.replace(LogMessageSearcher.getMessagePrefix(), "") + "\n"; // Remove prefix
            }
        } catch (IOException e) {
            reportingExtraMessage = "Failure while retrieving messages from logfile.";
            log.log(Level.SEVERE, "Failure while retrieving messages from logfile.", e);
        }

        if (build.getResult() != Result.SUCCESS) {
            reportingExtraMessage += "\nGatekeepering and Upmerging have been aborted, " +
                    "and no further actions were performed, because the build failed.";
        }


        /* Retrieve case */
        FogbugzManager caseManager = this.getFogbugzManager();
        FogbugzCase fbCase = null;
        try {
            fbCase = caseManager.getCaseById(usableCaseId);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Fetching case from fogbugz failed. Please check your settings.", e);
            listener.getLogger().append("Fetching case from Fogbugz failed. Please check your settings.");
            return false;
        }
        if (fbCase == null) {
            log.log(Level.SEVERE, "Fetching case from fogbugz failed. Please check your settings.");
            return false;
        }

        FogbugzEvent lastAssignmentEvent = caseManager.getLastAssignedToGatekeepersEvent(fbCase.getId());

        /* Fill template context with useful variables. */
        Context templateContext = Context.newContext(null);
        templateContext.data("url", build.getAbsoluteUrl());
        templateContext.data("buildNumber", Integer.toString(build.getNumber()));
        templateContext.data("buildResult", build.getResult().toString());
        log.log(Level.FINE, "ReportingExtraMessage: " + reportingExtraMessage);
        templateContext.data(
                "messages", StringEscapeUtils.unescapeXml(StringEscapeUtils.unescapeHtml(reportingExtraMessage)));
        try {
            AbstractTestResultAction testResultAction = build.getTestResultAction();
            templateContext.data("tests_failed", testResultAction.getFailCount());
            templateContext.data("tests_skipped", testResultAction.getSkipCount());
            templateContext.data("tests_total", testResultAction.getTotalCount());
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception during fetching of test results:", e);
            templateContext.data("tests_failed", "");
            templateContext.data("tests_skipped", "");
            templateContext.data("tests_total", "");
        }

        /* Assign the case back to detected developer. */
        if (this.getDescriptor().doAssignBaseCase()) {
            try {
                fbCase.setAssignedTo(lastAssignmentEvent.getPerson());
            } catch (Exception e) {
                fbCase.setAssignedTo(this.getDescriptor().getGatekeeperUserId());
            }
        }

        /* Fetch&render templates, then save the template output together with the case. */
        Template mustacheTemplate;
        Handlebars handlebars = new Handlebars();
        if (build.getResult() == Result.SUCCESS) {
            try {
                mustacheTemplate = handlebars.compileInline(this.getDescriptor().getSuccessfulBuildTemplate());
            }
            catch (IOException e) {
                mustacheTemplate = null;
            }
            // Add tag if required
            if (this.getDescriptor().getSuccessfulBuildTag() != null && !this.getDescriptor().getSuccessfulBuildTag().isEmpty()) {
                fbCase.addTag(this.getDescriptor().getSuccessfulBuildTag());
            }

            // Set milestone if required
            // TODO: again, replace this with an extension point, as this is very process specific.
            if (this.getDescriptor().doAssignBaseCase()) {
                // Milestone should be set to 'target branch' without the 'r' in front of the release number.
                // Strip 'r' from release number and set as milestone (creating one if not exists).
                String milestoneName = fbCase.getTargetBranch().substring(1, fbCase.getTargetBranch().length());
                caseManager.createMilestoneIfNotExists(milestoneName);
                fbCase.setMilestone(milestoneName);
            }
        } else {
            try {
                mustacheTemplate = handlebars.compileInline(this.getDescriptor().getFailedBuildTemplate());
            }
            catch (IOException e) {
                mustacheTemplate = null;
            }
        }
        /* Save case, this propagates the changes made on the case object */
        String message = "Error rendering template during reporting! Please check jenkins configuration.";
        if (mustacheTemplate != null) {
            try {
                message = mustacheTemplate.apply(templateContext);
            }
            catch (IOException e) {
            }
        }
        caseManager.saveCase(fbCase, message);

        return true;
    }

    public void notifyScheduled(FogbugzCase fbCase, Project<?, ?> p) {
        Context templateContext = Context.newContext(null);
        templateContext.data("url", p.getAbsoluteUrl());
        templateContext.data("name", p.getName());
        Template mustacheTemplate;
        Handlebars handlebars = new Handlebars();
        try {
            mustacheTemplate = handlebars.compileInline(this.getDescriptor().getScheduledBuildTemplate());
        }
        catch (IOException e) {
            mustacheTemplate = null;
        }
        /* Save case, this propagates the changes made on the case object */
        String message = "Error rendering scheduled template during reporting! Please check jenkins configuration.";
        if (mustacheTemplate != null) {
            try {
                message = mustacheTemplate.apply(templateContext);
            }
            catch (IOException e) {
            }
        }
        this.getFogbugzManager().saveCase(fbCase, message);
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Deprecated
    public FogbugzManager getFogbugzCaseManager() {
        return this.getDescriptor().getFogbugzCaseManager();
    }

    public FogbugzManager getFogbugzManager() {
        return this.getDescriptor().getFogbugzManager();
    }


    /**
     * Global settings for FogbugzPlugin.
     * Suggestion: move this to it's own class to keep this file small.
     */
    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Getter private String token;

        private String failedBuildTemplate;
        private String successfulBuildTemplate;
        private String scheduledBuildTemplate;

        @Getter private String featureBranchFieldname;
        @Getter private String originalBranchFieldname;
        @Getter private String targetBranchFieldname;
        @Getter private String approvedRevisionFieldname;
        @Getter private String ciProjectFieldName;

        private int mergekeeperUserId;
        private int gatekeeperUserId;

        @Getter private boolean assignBackCase = true;
        @Getter private boolean setMilestone = true;
        @Getter private String successfulBuildTag = "";

        public String getUrl() {
            return new FogbugzProjectProperty().getDescriptor().getUrl();
        }

        public boolean doAssignBaseCase() {
            return this.assignBackCase;
        }

        public boolean doSetMilestone() {
            return this.setMilestone;
        }

        public int getMergekeeperUserId() {
            if (this.mergekeeperUserId == 0) {
                return 1;
            } else {
                return this.mergekeeperUserId;
            }
        }

        public int getGatekeeperUserId() {
            if (this.gatekeeperUserId == 0) {
                return 1;
            } else {
                return this.gatekeeperUserId;
            }
        }

        public String getFailedBuildTemplate() {
            if (this.failedBuildTemplate == null || this.failedBuildTemplate.isEmpty()) {
                return "Jenkins reports that the build has {{tests_failed}} failed tests :(" +
                        "\nCatched log messages:\n{{messages}}" +
                        "\nView extended result here: {{url}}";
            } else {
                return this.failedBuildTemplate;
            }
        }

        public String getSuccessfulBuildTemplate() {
            if (this.successfulBuildTemplate == null || this.successfulBuildTemplate.isEmpty()) {
                return "Jenkins reports that the build was successful!" +
                        "\nCatched log messages:\n{{messages}}" +
                        "\nView extended result here: {{url}}";
            } else {
                return this.successfulBuildTemplate;
            }
        }


        public String getScheduledBuildTemplate() {
            if (this.scheduledBuildTemplate == null || this.scheduledBuildTemplate.isEmpty()) {
                return "Scheduled a jenkins build on a {{name}} job. Stay tuned!" +
                        "\nView the job here: {{url}}";
            } else {
                return this.scheduledBuildTemplate;
            }
        }

        public DescriptorImpl() {
            super();
            load();
        }

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> type) {
            return true;
		}

		@Override
		public String getDisplayName() {
            return "Report status to related FogBugz case.";
		}

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            this.token = formData.getString("token");
            this.featureBranchFieldname = formData.getString("featureBranchFieldname");
            this.originalBranchFieldname = formData.getString("originalBranchFieldname");
            this.targetBranchFieldname = formData.getString("targetBranchFieldname");
            this.approvedRevisionFieldname = formData.getString("approvedRevisionFieldname");
            this.ciProjectFieldName = formData.getString("ciProjectFieldName");

            int mergekeeperid = formData.getInt("mergekeeperUserId");
            if (mergekeeperid == 0) {
                mergekeeperid = 1;
            }
            this.mergekeeperUserId = mergekeeperid;

            int gatekeeperid = formData.getInt("gatekeeperUserId");
            if (gatekeeperid == 0) {
                gatekeeperid = 1;
            }
            this.gatekeeperUserId = gatekeeperid;

            this.failedBuildTemplate = formData.getString("failedBuildTemplate");
            this.successfulBuildTemplate = formData.getString("successfulBuildTemplate");
            this.scheduledBuildTemplate = formData.getString("scheduledBuildTemplate");

            this.assignBackCase = formData.getBoolean("assignBackCase");
            this.setMilestone = formData.getBoolean("setMilestone");
            this.successfulBuildTag = formData.getString("successfulBuildTag");

            save();
            return super.configure(req, formData);
        }

        @Deprecated
        public FogbugzManager getFogbugzCaseManager() {
            return this.getFogbugzManager();
        }

        public FogbugzManager getFogbugzManager() {
            return new FogbugzManager(this.getUrl(), this.getToken(), this.getFeatureBranchFieldname(),
                    this.getOriginalBranchFieldname(), this.getTargetBranchFieldname(), this.getApprovedRevisionFieldname(),
                    this.getCiProjectFieldName(),
                    this.getMergekeeperUserId(), this.getGatekeeperUserId());
        }

	}
}

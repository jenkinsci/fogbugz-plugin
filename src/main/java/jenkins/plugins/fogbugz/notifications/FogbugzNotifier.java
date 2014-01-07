package jenkins.plugins.fogbugz.notifications;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import jenkins.plugins.fogbugz.FogbugzProjectProperty;
import org.apache.commons.lang.StringEscapeUtils;

import hudson.EnvVars;
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
import org.paylogic.fogbugz.FogbugzCaseManager;
import org.paylogic.fogbugz.FogbugzEvent;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Notifier in Jenkins system, reports status to Fogbugz.
 * Searches for branches compatible with caseID, and reports if that's found.
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

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        log.info("Now performing post-build action for Fogbugz reporting.");
        PrintStream l = listener.getLogger();
        l.println("----------------------------------------------------------");
        l.println("----------- Now sending build status to FogBugz ----------");
        l.println("----------------------------------------------------------");

        String givenCaseId = (String) build.getEnvVars().get("CASE_ID");

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
        FogbugzCaseManager caseManager = this.getFogbugzCaseManager();
        FogbugzCase fbCase = caseManager.getCaseById(usableCaseId);
        if (fbCase == null) {
            log.log(Level.SEVERE, "Fetching case from fogbugz failed. Please check your settings.");
            return false;
        }

        FogbugzEvent lastAssignmentEvent = caseManager.getLastAssignedToGatekeepersEvent(fbCase.getId());

        /* Fill template context with useful variables. */
        Map<String, String> templateContext = new HashMap();
        templateContext.put("url", build.getAbsoluteUrl());
        templateContext.put("buildNumber", Integer.toString(build.getNumber()));
        templateContext.put("buildResult", build.getResult().toString());
        templateContext.put("messages", StringEscapeUtils.unescapeHtml(reportingExtraMessage));
        try {
            templateContext.put("tests_failed", Integer.toString(build.getTestResultAction().getFailCount()));
            templateContext.put("tests_skipped", Integer.toString(build.getTestResultAction().getSkipCount()));
            templateContext.put("tests_total", Integer.toString(build.getTestResultAction().getTotalCount()));
        } catch (Exception e) {
            log.log(Level.SEVERE, "Exception during fetching of test results:", e);
            templateContext.put("tests_failed", "unknown");
            templateContext.put("tests_skipped", "unknown");
            templateContext.put("tests_total", "unknown");
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
        if (build.getResult() == Result.SUCCESS) {
            mustacheTemplate = Mustache.compiler().compile(this.getDescriptor().getSuccessfulBuildTemplate());

            // Add tag if required
            if (this.getDescriptor().getSuccessfulBuildTag() != null && !this.getDescriptor().getSuccessfulBuildTag().isEmpty()) {
                fbCase.addTag(this.getDescriptor().getSuccessfulBuildTag());
            }

            // Set milestone if required
            // TODO: again, replace this with an extension point, as this is very process specific.
            if (this.getDescriptor().doAssignBaseCase()) {
                // Milestone should be set to 'target branch' without the 'r' in front of the release number.
                if (fbCase.getTargetBranch().matches(this.getReleaseBranchRegex())) {
                    // Strip 'r' from release number and set as milestone.
                    fbCase.setMilestone(fbCase.getTargetBranch().substring(1, fbCase.getTargetBranch().length()));
                }
            }
        } else {
            mustacheTemplate = Mustache.compiler().compile(this.getDescriptor().getFailedBuildTemplate());
        }
        /* Save case, this propagates the changes made on the case object */
        caseManager.saveCase(fbCase, mustacheTemplate.execute(templateContext));

        return true;
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    public FogbugzCaseManager getFogbugzCaseManager() {
        return this.getDescriptor().getFogbugzCaseManager();
    }


    public String getReleaseBranchRegex() {
        return this.getDescriptor().getReleaseBranchRegex();
    }

    public String getFeatureBranchRegex() {
        return this.getDescriptor().getFeatureBranchRegex();
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

        @Getter private String featureBranchFieldname;
        @Getter private String originalBranchFieldname;
        @Getter private String targetBranchFieldname;

        @Getter private String job_to_trigger;

        private int mergekeeperUserId;
        private int gatekeeperUserId;

        @Getter private boolean assignBackCase = true;
        @Getter private boolean setMilestone = true;
        @Getter private String successfulBuildTag = "";

        @Getter private String featureBranchRegex = "c\\d+";
        @Getter private String releaseBranchRegex = "r\\d{4}";

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
            this.job_to_trigger = formData.getString("job_to_trigger");

            this.assignBackCase = formData.getBoolean("assignBackCase");
            this.setMilestone = formData.getBoolean("setMilestone");
            this.successfulBuildTag = formData.getString("successfulBuildTag");

            this.releaseBranchRegex = formData.getString("releaseBranchRegex");
            this.featureBranchRegex = formData.getString("featureBranchRegex");

            save();
            return super.configure(req, formData);
        }

        public FogbugzCaseManager getFogbugzCaseManager() {
            return new FogbugzCaseManager(this.getUrl(), this.getToken(), this.getFeatureBranchFieldname(),
                                             this.getOriginalBranchFieldname(), this.getTargetBranchFieldname(),
                                             this.getMergekeeperUserId(), this.getGatekeeperUserId());
        }

	}
}

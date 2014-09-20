package jenkins.plugins.fogbugz.casecreator;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.AggregatedTestResultPublisher;
import hudson.tasks.test.TestResult;
import jenkins.plugins.fogbugz.notifications.FogbugzNotifier;
import lombok.extern.java.Log;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.paylogic.fogbugz.FogbugzCase;
import org.paylogic.fogbugz.FogbugzManager;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * This extension is meant to be used without the others.
 * It will create a new case in Fogbugz when a build starts to fail.
 */
@Log
public class FogbugzCaseCreator extends Notifier {

    @DataBoundConstructor
    public FogbugzCaseCreator() {}


    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {

        try {
            AbstractTestResultAction resultAction = build.getAction(AbstractTestResultAction.class);
            if (resultAction == null) {
                return true;
            }
            int currentFailingTests = resultAction.getFailCount();
            int previousFailingTests = build.getPreviousBuild().getAction(AbstractTestResultAction.class).getFailCount();

            boolean newFailedTests = false;
            List<String> newFailingTestsList = new ArrayList();
            for (Object cr : build.getAction(AbstractTestResultAction.class).getFailedTests()) {
                CaseResult rr = (CaseResult) cr;
                if (rr.isFailed() && !rr.getPreviousResult().isFailed()) {
                    // New failing test. Report new failing even if this is the only one.
                    newFailedTests = true;
                    newFailingTestsList.add(rr.getName());
                }
            }

            if (newFailedTests) {
                // We have new failing tests it seems.
                FogbugzNotifier notifier = new FogbugzNotifier();
                FogbugzManager caseManager = notifier.getFogbugzManager();
                FogbugzCase fbCase = new FogbugzCase(
                        0, // ID of 0 should create new case.
                        String.format("New failing tests in Jenkins job '%s'", build.getProject().getName()), // Case title
                        notifier.getDescriptor().getMergekeeperUserId(), // OpenedBy
                        notifier.getDescriptor().getMergekeeperUserId(),  // AssignedTo
                        "autocreated", // tags
                        true, // isOpen
                        "", "", "", "", "", // feature-, target- and originalbranch + approved revision + ci project
                        "" // milestone
                );

                boolean fbResult =  caseManager.saveCase(
                        fbCase, String.format("Job '%s' has %d more failing tests than the previous build.\n" +
                        "New failing tests:\n%s\nView complete result here: %s.", // TODO: customizable message
                            build.getProject().getName(),
                            currentFailingTests - previousFailingTests,
                            StringUtils.join(newFailingTestsList, ",\n"),
                            build.getAbsoluteUrl()
                        )
                );

                if (!fbResult) {
                    log.log(Level.SEVERE, "Something went wrong while creating a new case.");
                }
            }


        } catch (Exception e) {
            // Its not important for this plugin to succeed, so ignore exceptions, but log them.
            log.log(Level.INFO, "Exception in FogbugzCaseCreator.", e);
        }
        return true;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Create new FogBugz case on new test failures.";
        }
    }
}

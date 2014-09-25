package jenkins.plugins.fogbugz.jobtrigger;

import hudson.Extension;
import hudson.model.*;
import jenkins.model.Jenkins;
import jenkins.plugins.fogbugz.notifications.FogbugzNotifier;
import lombok.extern.java.Log;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerResponse;
import org.paylogic.fogbugz.FogbugzCase;
import org.paylogic.fogbugz.FogbugzManager;
import org.paylogic.fogbugz.InvalidResponseException;
import org.paylogic.fogbugz.NoSuchCaseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;


/**
 * FogbugzEventListener: a HTTP endpoint that triggers builds.
 * Set the build to trigger in Jenkins' global settings page.
 * If you don't set a build, the trigger will do noting and quit immediately.
 */
@Log
@Extension
public class FogbugzEventListener implements UnprotectedRootAction {

    private static String NO_CASE_FOUND_RESPONSE = "<html><body>No case found</body></html>";

    private static String FOGBUGZ_ERROR_RESPONSE = "<html><body>Error communicating with fogbugz</body></html>";

    private static String CASE_IS_AUTOCREATED_RESPONSE = "<html><body>Case is autocreated, skipping</body></html>";

    private static String CASE_IS_MERGED_RESPONSE = "<html><body>Case is already merged, skipping</body></html>";

    private static String OK_RESPONSE = "<html><body>Scheduled ok</body></html>";

    private static String NOTHING_RESPONSE = "<html><body>Nothing was sceduled. Note that anonymous has to have a job READ permission.</body></html>";

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "Fogbugz Event Listener";
    }

    public String getUrlName() {
        return "fbTrigger";
    }

    public void doIndex(StaplerResponse rsp, @QueryParameter(required = true) int caseid,
                          @QueryParameter(required = false) String jobname,
                          @QueryParameter(required = false) String jobnamepostfix,
                          @QueryParameter(required = false) String ciprojectfieldname) throws IOException {
        rsp.setContentType("text/html");
        log.info("Fogbugz URLTrigger received, processing...");
        FogbugzNotifier fbNotifier = new FogbugzNotifier();
        String response = scheduleJob(fbNotifier, caseid, jobname, jobnamepostfix, ciprojectfieldname, true);
        rsp.getOutputStream().write(response.getBytes());
    }

    public String scheduleJob(FogbugzNotifier fbNotifier, int caseid, String jobname, String jobnamepostfix,
                              String ciprojectfieldname, boolean sendNotification) {
        if (caseid < 1) {
            return NO_CASE_FOUND_RESPONSE;
        }

        if (jobnamepostfix == null) {
            jobnamepostfix = "";
        }

        assert jobnamepostfix != null;

        FogbugzManager caseManager = fbNotifier.getFogbugzManager();
        if (ciprojectfieldname != null && !ciprojectfieldname.isEmpty()) {
            caseManager.setCiProjectFieldName(ciprojectfieldname);
        }
        FogbugzCase fbCase = null;
        try {
            fbCase = caseManager.getCaseById(caseid);
        } catch (NoSuchCaseException e) {
            log.log(Level.INFO, "No case found with this id, not triggering anything.", caseid);
            return NO_CASE_FOUND_RESPONSE;
        } catch (InvalidResponseException e) {
            log.log(Level.INFO, "Error getting case by id.", e);
            return FOGBUGZ_ERROR_RESPONSE;
        }

        FogbugzNotifier.DescriptorImpl descriptor = fbNotifier.getDescriptor();
        String successfulBuildTag = (descriptor != null) ? descriptor.getSuccessfulBuildTag() : "";

        if (fbCase.hasTag("autocreated")) {
            // Then do not process this case until the tag is removed.
            return CASE_IS_AUTOCREATED_RESPONSE;
        }
        else if (successfulBuildTag != null && !successfulBuildTag.isEmpty() && fbCase.getTags().contains(successfulBuildTag)) {
            return CASE_IS_MERGED_RESPONSE;
        }

        String ciProject = fbCase.getCiProject();

        log.log(Level.INFO, "Ci project field value: " + ciProject);

        if (ciProject != null && !ciProject.isEmpty()) {
            jobname =  ciProject + jobnamepostfix;
        }

        log.log(Level.INFO, "Looking for job with name: " + jobname);

        // Search for Job that'll be triggered.
        for (Project<?, ?> p: Jenkins.getInstance().getItems(Project.class)) {
            if (p.getName().equals(jobname)) {
                // Fetch default Parameters
                final List<ParameterValue> parameters = new ArrayList<ParameterValue>();
                ParametersDefinitionProperty property = p.getProperty(ParametersDefinitionProperty.class);
                if (property != null) {
                    for (final ParameterDefinition pd : property.getParameterDefinitions()) {
                        final ParameterValue param = pd.getDefaultParameterValue();
                        // Fill in CASE_ID
                        if (pd.getName().equals("CASE_ID")) {  // Override CASE_ID param if it's there.
                            parameters.add(new StringParameterValue("CASE_ID", Integer.toString(fbCase.getId())));
                            // Else, just add the value already set.
                        } else if (param != null) {
                            parameters.add(param);
                        }
                    }
                }
                // Here, we actually schedule the build.
                p.scheduleBuild2(0, new FogbugzBuildCause(), new ParametersAction(parameters));
                if (sendNotification) {
                    fbNotifier.notifyScheduled(fbCase, p);
                }
                return OK_RESPONSE;
            }
        }
        return NOTHING_RESPONSE;
    }
}

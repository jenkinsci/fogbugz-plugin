package jenkins.plugins.fogbugz.jobtrigger;

import hudson.Extension;
import hudson.model.*;
import jenkins.model.Jenkins;
import jenkins.plugins.fogbugz.notifications.FogbugzNotifier;
import lombok.extern.java.Log;
import org.kohsuke.stapler.QueryParameter;
import org.paylogic.fogbugz.FogbugzCase;
import org.paylogic.fogbugz.FogbugzManager;

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

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "Fogbugz Event Listener";
    }

    public String getUrlName() {
        return "fbTrigger";
    }

    public void doIndex(@QueryParameter(required = true) int caseid, @QueryParameter(required = true) String jobname) {
        log.info("Fogbugz URLTrigger received, processing...");
        scheduleJob(caseid, jobname);
    }

    public void scheduleJob(int caseid, String jobname) {
        if (caseid == 0) {
            return;
        }

        FogbugzNotifier fbNotifier = new FogbugzNotifier();

        FogbugzManager caseManager = fbNotifier.getFogbugzManager();
        FogbugzCase fbCase = null;
        try {
            fbCase = caseManager.getCaseById(caseid);
        } catch (Exception e) {
            log.log(Level.INFO, "No case found with this id, not triggering anything.", e);
            return;
        }

        if (fbCase.hasTag("autocreated")) {
            // Then do not process this case until the tag is removed.
            return;
        }

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
                fbNotifier.notifyScheduled(fbCase, p);
            }
        }
    }
}

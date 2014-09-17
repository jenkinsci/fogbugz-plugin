package jenkins.plugins.fogbugz.jobpoller;

import antlr.ANTLRException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.Queue;
import hudson.triggers.TimerTrigger;
import hudson.triggers.TriggerDescriptor;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

import hudson.util.LogTaskListener;
import jenkins.plugins.fogbugz.jobtrigger.FogbugzEventListener;
import jenkins.plugins.fogbugz.notifications.FogbugzNotifier;
import lombok.extern.java.Log;
import org.kohsuke.stapler.DataBoundConstructor;
import org.paylogic.fogbugz.FogbugzCase;
import org.paylogic.fogbugz.FogbugzManager;
import org.paylogic.fogbugz.FogbugzUser;
import org.paylogic.fogbugz.InvalidResponseException;
import org.paylogic.fogbugz.NoSuchCaseException;

/**
 * Fogbugz build poller.
 */
@Log
public class FogbugzStatePoller extends TimerTrigger {

    public final String ciProject;
    public final String ciProjectField;

    @DataBoundConstructor
    public FogbugzStatePoller(String spec, String ciProject, String ciProjectField) throws ANTLRException {
        super(spec);
        this.ciProject = ciProject;
        this.ciProjectField = ciProjectField;
    }

    @Override
    public void run() {
        log.info("Running FogbugzStatePoller for project " + ciProject + ".");
        FogbugzNotifier fb = new FogbugzNotifier();
        FogbugzManager fbManager = fb.getFogbugzManager();
        FogbugzEventListener fbListener = new FogbugzEventListener();

        doRun(fb, fbManager, fbListener);

    }

    public void doRun(FogbugzNotifier fb, FogbugzManager fbManager, FogbugzEventListener fbListener) {
        FogbugzUser user = fbManager.getFogbugzUser(fbManager.getMergekeeperUserId());

        List<FogbugzCase> cases;
        try {
            String query = "%s:\"%s\" assignedto:\"%s\"";
            query = String.format(query, this.ciProjectField, this.ciProject, user.name);
            cases = fbManager.searchForCases(query);
        } catch (InvalidResponseException e) {
            log.log(Level.SEVERE, "FogbugzStatePoller encountered an error while getting project list.");
            log.log(Level.SEVERE, e.getMessage());
            return;
        } catch (NoSuchCaseException e) {
            log.info("FogbugzStatePoller found no cases, not running.");
            return;
        }
        for (FogbugzCase cs : cases) {
            Job job = (Job) this.job;
            int case_id = cs.getId();
            String case_id_string = case_id + "";
            if (job.isBuilding()) {
                try {
                    EnvVars vars = job.getLastBuild().getEnvironment(new LogTaskListener(log, Level.INFO));
                    if (vars.get("CASE_ID", "").trim().equals(case_id_string)) {
                        continue;
                    }
                } catch (InterruptedException exc) {
                } catch (IOException exc) {
                }
            }
            fbListener.scheduleJob(fb, case_id, job.getName(), null, null, false);
        }
    }

    @Extension
    public static final class DescriptorImpl extends TriggerDescriptor {

        @Override
        public boolean isApplicable(Item item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Poll Fogbugz for cases waiting for mergekeepering.";
        }

    }
}

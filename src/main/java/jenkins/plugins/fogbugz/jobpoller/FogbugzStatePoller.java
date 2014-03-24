package jenkins.plugins.fogbugz.jobpoller;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.Item;
import hudson.triggers.TimerTrigger;
import hudson.triggers.TriggerDescriptor;
import java.util.List;
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

    @DataBoundConstructor
    public FogbugzStatePoller(String spec, String ciProject) throws ANTLRException {
        super(spec);
        this.ciProject = ciProject;
    }

    @Override
    public void run() {
        log.info("Running FogbugzStatePoller for project " + ciProject + ".");
        FogbugzNotifier fb = new FogbugzNotifier();
        FogbugzManager fbManager = fb.getFogbugzManager();
        FogbugzEventListener fbListener = new FogbugzEventListener();

        FogbugzUser user = fbManager.getFogbugzUser(fbManager.getMergekeeperUserId());

        List<FogbugzCase> cases;
        try {
            cases = fbManager.searchForCases("cixproject:\"" + this.ciProject + "\" assignedto:\"" + user.name + "\"");
        } catch (InvalidResponseException e) {
            log.info("FogbugzStatePoller encountered an error while getting project list.");
            log.info(e.getMessage());
            return;
        } catch (NoSuchCaseException e) {
            log.info("FogbugzStatePoller found no cases, not running.");
            return;
        }
        for (FogbugzCase c : cases) {
            if (!c.getTags().contains("merging")) {
                c.addTag("merging");
                fbManager.saveCase(c);
                fbListener.doIndex(c.getId());
            }
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

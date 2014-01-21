package jenkins.plugins.fogbugz.periodicwork;

import hudson.Extension;
import hudson.model.AperiodicWork;
import jenkins.plugins.fogbugz.jobtrigger.FogbugzEventListener;
import jenkins.plugins.fogbugz.notifications.FogbugzNotifier;
import lombok.extern.java.Log;
import org.paylogic.fogbugz.*;

import java.util.List;
import java.util.logging.Level;

@Extension
@Log
public class FogbugzPeriodicWorkPoller extends AperiodicWork {

    private static final long DEFAULT_RECCURENCE_TIME = 360;
    private static final long INITIAL_DELAY_TIME = 120;

    private volatile boolean stopRequested;
    private long reccurencePeriod;

    public FogbugzPeriodicWorkPoller() {
        this(DEFAULT_RECCURENCE_TIME, false);
    }

    public FogbugzPeriodicWorkPoller(long recurrencePeriod, boolean stopRequested) {
        this.reccurencePeriod = recurrencePeriod;
        this.stopRequested = stopRequested;
    }

    @Override
    public long getRecurrencePeriod() {
        return reccurencePeriod;
    }

    public long getInitialDelayTime() {
        return INITIAL_DELAY_TIME;
    }

    @Override
    public AperiodicWork getNewInstance() {
        return new FogbugzPeriodicWorkPoller(reccurencePeriod, stopRequested);
    }

    @Override
    protected void doAperiodicRun() {
        if (!stopRequested) {
            FogbugzNotifier fbNotifer = new FogbugzNotifier();
            FogbugzManager manager = fbNotifer.getFogbugzManager();
            String mergekeeperFullname = fbNotifer.getDescriptor().getMergekeeperFullName();

            try {
                List<FogbugzCase> cases =  manager.searchForCases("assignedto:\"" + mergekeeperFullname + "\"");
                for (FogbugzCase fbCase : cases) {
                    if (!fbCase.hasTag("ci-scheduled") && !fbCase.hasTag("autocreated")) {
                        fbCase.addTag("ci-scheduled");
                        FogbugzEventListener.scheduleBuildForCase(fbCase);
                        manager.saveCase(fbCase, "Jenkins scheduled a Mergekeeping job for this case, " +
                                "and will report back here when it's done.");
                    }
                }

            } catch (InvalidResponseException e) {
                log.log(Level.SEVERE, "Exception during polling of cases", e);
            } catch (NoSuchCaseException e) {
                // pass
            }

        }
    }

    public void stop() {
        this.stopRequested = true;
    }

    public void start() {
        this.stopRequested = false;
    }

    public static FogbugzPeriodicWorkPoller get() {
        return AperiodicWork.all().get(FogbugzPeriodicWorkPoller.class);
    }
}

package jenkins.plugins.fogbugz.jobtrigger;

import hudson.model.Cause;

public class FogbugzBuildCause extends Cause {
    @Override
    public String getShortDescription() {
        return "Triggered by http event from Fogbugz URLtrigger.";
    }
}

package jenkins.plugins.fogbugz.buildactions;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import lombok.extern.java.Log;

/**
 * FogbugzLinkDescriptor: Jenkins entry point for the plugin that puts Fogbugz links on build pages.
 */
@Log
@Extension
public class FogbugzLinkDescriptor extends BuildStepDescriptor<Publisher> {

    public FogbugzLinkDescriptor() {
        super(FogbugzLinkBuilder.class);
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
        return true;
    }

    @Override
    public String getDisplayName() {
        return "Add FogBugz link to related case on build page";
    }
}

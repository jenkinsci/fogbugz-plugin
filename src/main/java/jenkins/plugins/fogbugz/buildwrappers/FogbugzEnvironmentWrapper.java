package jenkins.plugins.fogbugz.buildwrappers;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.plugins.fogbugz.notifications.FogbugzNotifier;
import lombok.Getter;
import lombok.extern.java.Log;
import org.kohsuke.stapler.DataBoundConstructor;
import org.paylogic.fogbugz.FogbugzCase;
import org.paylogic.fogbugz.FogbugzManager;

import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log
public class FogbugzEnvironmentWrapper extends BuildWrapper {

    /**
     * This expression derived/taken from the BNF for URI (RFC2396).
     * Validates URLs
     */

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {
        @Override
        public String getDisplayName() {
            return "Copy custom fields from Fogbugz case retrieved by 'CASE_ID' environment variable into " +
                    "environment variables available to other build steps.";
        }

        @Override
        public boolean isApplicable(final AbstractProject<?, ?> item) {
            return true;
        }
    }

    @Getter private final boolean copyInformation;
    @Getter private final String repoBase;

    @DataBoundConstructor
    public FogbugzEnvironmentWrapper(final boolean copyInformation, final String repoBase) {
        super();
        this.copyInformation = copyInformation;
        this.repoBase = repoBase;
    }

    /**
     * Parses feature branch field of fogbugz case and comes up with full and valid full repo url.
     * @throws Exception
     */
    public String parseRepoUrl(String featureBranchField) throws Exception {

        String repoBase = getRepoBase();

        URI baseRepoUrl = new URI(repoBase);
        URI featureRepoUrl = new URI(featureBranchField);

        URI res = baseRepoUrl.resolve(featureRepoUrl);
        // TODO: ugly workaround for mercurial
        if (repoBase.contains(baseRepoUrl.getHost() + "//")
                || featureBranchField.toString().contains(featureRepoUrl.getHost() + "//"))
            return res.toString().replace(res.getHost() + "/",  res.getHost() + "//").replace("///", "//");
        return res.toString();
    }

    /**
     * Parses the branch name from fogbugz case.
     */
    public static String parseBranchName(String branchExpression) {
        String [] parts = branchExpression.split("#");
        if (parts.length > 1) {
            return parts[parts.length - 1];
        }
        return branchExpression;
    }

    /**
     * Retrieves data from Fogbugz, and puts it into environment variables.
     */
    public BuildWrapper.Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) {
        EnvVars envVars = new EnvVars();

        EnvVars currentEnv;
        try {
            currentEnv = build.getEnvironment(listener);
        } catch (Exception e) {
            log.log(Level.INFO, "Failure while retrieving existing environment for build.", e);
            return null;
        }

        // Fetch case
        int caseId = Integer.parseInt(currentEnv.get("CASE_ID", ""));
        FogbugzManager manager = new FogbugzNotifier().getFogbugzManager();
        FogbugzCase fbCase = null;
        try {
            fbCase = manager.getCaseById(caseId);
        } catch (Exception e) {
            log.log(Level.INFO, "Exception while fetching case from Fogbugz", e);
            return null;
        }

        // If repo base exists, fill in REPO_URL as well.
        if (getRepoBase() != null && !getRepoBase().isEmpty()) {
            try {
                String repoUrl = this.parseRepoUrl(fbCase.getFeatureBranch());
                envVars.put("REPO_URL", repoUrl);
            } catch (Exception e) {
                log.log(Level.INFO, "Error while parsing repo url, not continuing with build", e);
                return null;
            }
        }

        // Put case values into map
        envVars.put("TARGET_BRANCH", this.parseBranchName(fbCase.getTargetBranch()));
        envVars.put("ORIGINAL_BRANCH", this.parseBranchName(fbCase.getOriginalBranch()));
        envVars.put("FEATURE_BRANCH", this.parseBranchName(fbCase.getFeatureBranch()));
        envVars.put("APPROVED_REVISION", fbCase.getApprovedRevision());
        final Map<String, String> finalEnvVars = (Map<String, String>) envVars.clone();

        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                env.putAll(finalEnvVars);
            }
        };
    }
}

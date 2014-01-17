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
    private static final String URL_REGEX = "^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?";
    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);


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
    private String parseRepoUrl(String featureBranchField) throws Exception {

        String repoBase = getRepoBase();
        Matcher baseUrlMatcher = URL_PATTERN.matcher(repoBase);
        Matcher repoUrlMatcher = URL_PATTERN.matcher(featureBranchField);
        String featureRepoUrl;

        // We seem to need to call these, otherwise 'group' method will not work.
        baseUrlMatcher.matches();
        repoUrlMatcher.matches();

            /* Logic for recognizing different formats or repository URLs */
        if (repoUrlMatcher.group(1) != null) {
            // If repo_path contains startswith one of: ssh:// http:// https://, we assume user put in full path and use that.
            featureRepoUrl = featureBranchField;

        } else if (repoUrlMatcher.group(5).contains(baseUrlMatcher.group(5).substring(1, baseUrlMatcher.group(5).length()))) {
            // if repo_path contains part of repoBase, this is probably in format /var/hg/repo#123, so we fix that.
            // strips first char from baseUrl group 5, which makes //var/hg /var/hg, or /var/hg var/hg, which makes the if match like we want.
            featureRepoUrl = baseUrlMatcher.group(1) + baseUrlMatcher.group(3);
            // If protocol is ssh, add extra /
            if (baseUrlMatcher.group(2).equals("ssh")) {
                featureRepoUrl += "/";
            }
            featureRepoUrl += featureBranchField;

        } else {
            // else we construct path with base from settings. format probably is users/repo#123 or repo#123, so that works.
            featureRepoUrl = this.getRepoBase() + featureBranchField;
        }

        // We test if the resulting URL is a valid one, if not, something went wrong and we quit.
        if (!featureRepoUrl.matches(URL_REGEX)) {
            throw new Exception("Error while constructing valid repository URL, we came up with " + featureRepoUrl);
        }

        return featureRepoUrl;
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
        envVars.put("TARGET_BRANCH", fbCase.getTargetBranch());
        envVars.put("ORIGINAL_BRANCH", fbCase.getOriginalBranch());
        envVars.put("FEATURE_BRANCH", fbCase.getFeatureBranch());
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

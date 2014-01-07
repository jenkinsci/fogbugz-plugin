package jenkins.plugins.fogbugz;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FogbugzProjectProperty extends JobProperty<AbstractProject<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(FogbugzProjectProperty.class.getName());

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends JobPropertyDescriptor {
        private String regex;
        private String url;

        public DescriptorImpl() {
            super(FogbugzProjectProperty.class);
            load();
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean isApplicable(Class<? extends Job> jobType) {
            return false;
        }

        @Override
        public String getDisplayName() {
            return "Fogbugz case annotater";
        }

        @Override
        public FogbugzProjectProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new FogbugzProjectProperty();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) {
            regex = req.getParameter("fogbugz.regex");
            url = req.getParameter("fogbugz.url");
            save();
            return true;
        }

        public String getUrl() {
            if (url == null) {
                return "http://fogbugz/";
            } else {
                return url;
            }
        }

        public String getRegex() {
            if (regex == null) {
                return "CASE-([0-9]*)";
            }
            return regex;
        }

        public FormValidation doRegexCheck(@QueryParameter(fixEmpty = true) String value) {
            if (value == null) {
                return FormValidation.error("No Case ID regex");
            }
            try {
                Pattern.compile(value);
                return FormValidation.ok();
            } catch (PatternSyntaxException e) {
                return FormValidation.error("Pattern cannot be compiled");
            }
        }

        /**
         * Checks if the FogBugz URL is accessible and exists.
         */
        public FormValidation doUrlCheck(@QueryParameter(fixEmpty = true) final String value)
                throws IOException, ServletException {
            if (!Hudson.getInstance().hasPermission(Hudson.ADMINISTER)) {
                return FormValidation.ok();
            }

            return new FormValidation.URLCheck() {
                @Override
                protected FormValidation check() throws IOException, ServletException {
                    String url = Util.fixEmpty(value);
                    if (url == null) {
                        return FormValidation.error("URL cannot be null");
                    }
                    try {
                        if (!findText(open(new URL(url)), "FogBugz")) {
                            return FormValidation.error("This is a valid URL but it doesn't look like FogBugz");
                        }

                        return FormValidation.ok();
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Unable to connect to " + url, e);
                        return handleIOException(url, e);
                    }
                }
            }.check();
        }

    }

}

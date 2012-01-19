package jenkins.plugins.fogbugz;

import hudson.Extension;
import hudson.MarkupText;
import hudson.MarkupText.SubText;
import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogAnnotator;
import hudson.scm.ChangeLogSet.Entry;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Extension
public class FogbugzChangelogAnnotator extends ChangeLogAnnotator {
    private final Logger logger = Logger.getLogger(getClass().getName());
    String showCaseUrlPart = "/default.asp?";

    private int getId(SubText token) {
        String id = null;
        for (int i = 0; ; i++) {
            id = token.group(i);

            try {
                return Integer.valueOf(id);
            } catch (NumberFormatException e) {
                logger.log(Level.FINE, "{0} is not a number in group {1}, trying next group", new Object[]{id, i});
                continue;
            }
        }
    }

    @Override
    public void annotate(AbstractBuild<?, ?> build, Entry change, MarkupText text) {
        Pattern pattern = null;
        String regex = FogbugzProjectProperty.DESCRIPTOR.getRegex();
        try {
            pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            logger.log(Level.WARNING, "Cannot compile pattern: {0}", regex);
            return;
        }

        for (SubText token : text.findTokens(pattern)) {
            Integer key = null;
            try {
                key = getId(token);
            } catch (Exception e) {
                continue;
            }
            token.surroundWith(String.format("<a href='%s%s%d'>", FogbugzProjectProperty.DESCRIPTOR.getUrl(), showCaseUrlPart, key), "</a>");
        }
    }

}

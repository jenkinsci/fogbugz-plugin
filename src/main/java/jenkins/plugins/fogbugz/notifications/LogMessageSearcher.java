package jenkins.plugins.fogbugz.notifications;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class that searches for reportable messages in build log.
 */
public class LogMessageSearcher {
    private AbstractBuild build;

    public static String DEFAULT_MESSAGE_KEYWORD = "[BuildReportMessage]";

    public LogMessageSearcher(AbstractBuild build) {
        this.build = build;
    }

    /**
     * Logs a message that is later findable by the searcher part of this class.
     * @param listener
     * @param message
     */
    public static void logMessage(BuildListener listener, String message) {
        listener.getLogger().append(getMessagePrefix() + message + "\n");
    }

    public static String getMessagePrefix() {
        return DEFAULT_MESSAGE_KEYWORD + " ";
    }

    public List<String> searchForMessages() throws IOException {
        // Search with default keyword to look for.
        return this.searchForMessages(DEFAULT_MESSAGE_KEYWORD);
    }

    /**
     * Searches build log for messages.
     * @param keyword Keyword to look for in log.
     * @return List of messages found.
     */
    public List<String> searchForMessages(String keyword) throws IOException {
        final File logFile = build.getLogFile();
        LineIterator it = FileUtils.lineIterator(logFile, "UTF-8");

        List<String> resultList = new ArrayList<String>();

        try {
            while (it.hasNext()) {
                String line = it.nextLine();
                if (line.contains(keyword)) {
                    resultList.add(line);
                }
            }
        } finally {
            it.close();
        }

        return resultList;
    }

}

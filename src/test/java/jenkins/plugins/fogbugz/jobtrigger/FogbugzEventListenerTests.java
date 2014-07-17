package jenkins.plugins.fogbugz.jobtrigger;

import hudson.model.FreeStyleProject;
import hudson.model.JobProperty;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import jenkins.plugins.fogbugz.notifications.FogbugzNotifier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.paylogic.fogbugz.FogbugzCase;
import org.paylogic.fogbugz.FogbugzManager;
import org.paylogic.fogbugz.NoSuchCaseException;

import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;


/**
 * Created by bubenkoff on 7/15/14.
 */

@RunWith(MockitoJUnitRunner.class)
public class FogbugzEventListenerTests {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Mock
    private FogbugzNotifier notifier;
    @Mock
    private FogbugzManager manager;

    @Test
    public void testFogbugzEventListener() throws Exception  {

        given(notifier.getFogbugzManager()).willReturn(manager);
        given(manager.getCaseById(1)).willThrow(new NoSuchCaseException("1"));
        FogbugzCase expected = new FogbugzCase(7, "HALLO!", 2, 2, "merged", true,
                "maikelwever/repo1#c7", "r1336", "r1336", "1336", "myproject", "some revision");
        given(manager.getCaseById(7)).willReturn(expected);

        String response = new FogbugzEventListener().scheduleJob(notifier, 0, "some job", null, null);
        assertEquals("<html><body>No case found</body></html>", response);

        response = new FogbugzEventListener().scheduleJob(notifier, 1, "some job", null, null);
        assertEquals("<html><body>No case found</body></html>", response);

        FreeStyleProject project = j.createFreeStyleProject("myproject_mergekeepers");
        JobProperty property = new ParametersDefinitionProperty(new StringParameterDefinition("CASE_ID", ""));
        project.addProperty(property);
        response = new FogbugzEventListener().scheduleJob(notifier, 7, null, "_mergekeepers", "cixproject");
        assertEquals("<html><body>Scheduled ok</body></html>", response);
        assertEquals("CASE_ID=7", project.getQueueItem().getParams().trim());
    }
}

package jenkins.plugins.fogbugz.jobpoller;

import hudson.Launcher;
import hudson.model.*;
import jenkins.plugins.fogbugz.jobtrigger.FogbugzEventListener;
import jenkins.plugins.fogbugz.notifications.FogbugzNotifier;
import lombok.extern.java.Log;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.paylogic.fogbugz.FogbugzCase;
import org.paylogic.fogbugz.FogbugzManager;
import org.paylogic.fogbugz.FogbugzUser;

import java.io.IOException;
import java.util.ArrayList;

import static java.lang.Thread.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyString;


@Log
@RunWith(MockitoJUnitRunner.class)
public class FogbugzStatePollerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Mock
    private FogbugzNotifier notifier;
    @Mock
    private FogbugzManager manager;


    @Test
    public void testRun() throws Exception {
        given(notifier.getFogbugzManager()).willReturn(manager);
        FogbugzUser fogbugzUser = new FogbugzUser(2, "test user");
        FogbugzCase expected = new FogbugzCase(7, "HALLO!", fogbugzUser.ix, fogbugzUser.ix, "merged", true,
                "maikelwever/repo1#c7", "r1336", "r1336", "1336", "myproject_mergekeepers", "some revision");
        given(manager.getCaseById(7)).willReturn(expected);
        given(manager.getFogbugzUser(2)).willReturn(fogbugzUser);
        given(manager.getMergekeeperUserId()).willReturn(2);
        ArrayList<FogbugzCase> cases = new ArrayList<FogbugzCase>();
        cases.add(expected);
        given(manager.searchForCases(anyString())).willReturn(cases);

        FreeStyleProject project = j.createFreeStyleProject("myproject_mergekeepers");
        j.setQuietPeriod(100);
        JobProperty property = new ParametersDefinitionProperty(new StringParameterDefinition("CASE_ID", ""));
        project.addProperty(property);
        FogbugzStatePoller poller = new FogbugzStatePoller("1 1 1 1 1", "myproject_mergekeepers", "cixproject");
        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                sleep(4000);
                return true;
            }
        });
        poller.start(project, false);
        poller.doRun(notifier, manager, new FogbugzEventListener());
        sleep(10);
        Queue.Item[] items = Queue.getInstance().getItems();
        assertEquals(1, items.length);
        assertEquals("CASE_ID=7", items[0].getParams().trim());
        sleep(100);
        assert project.isBuilding();
        // should not schedule the build if it's building with same case id
        poller.doRun(notifier, manager, new FogbugzEventListener());
        assert !project.isInQueue();
    }
}
package jenkins.plugins.fogbugz.buildwrappers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by bubenkoff on 1/21/14.
 */
public class FogbugzEnvironmentWrapperTest {

    @Test
    public void testParseRepoUrl() throws Exception {
        FogbugzEnvironmentWrapper wrapper = new FogbugzEnvironmentWrapper(true, "ssh://code.example.com//var/hg/users/");
        assertEquals(
                "ssh://code.example.com//var/hg/users/anatoly",
                wrapper.parseRepoUrl("anatoly#test"));

        assertEquals(
                "ssh://code.example.com//var/hg/users/something/other",
                wrapper.parseRepoUrl("something/other#test"));

        assertEquals(
                "ssh://another.code.example.com//var/something",
                wrapper.parseRepoUrl("ssh://another.code.example.com//var/something#other"));

        assertEquals(
                "ssh://another.code.example.com//var/something",
                wrapper.parseRepoUrl("hg+ssh://another.code.example.com//var/something#other"));

        assertEquals(
                "ssh://code.example.com//var/hg/unstable/paylogic_pta",
                wrapper.parseRepoUrl("/var/hg/unstable/paylogic_pta#pta1404"));

        assertEquals(
                "ssh://code.example.com//var/something/test",
                wrapper.parseRepoUrl("/var/something/test#pta1404"));

        wrapper = new FogbugzEnvironmentWrapper(true, "");
        assertEquals(
                "https://code.example.com/var/something/test",
                wrapper.parseRepoUrl("git+https://code.example.com/var/something/test#pta1404"));
        assertEquals(
                "user@git.example.com:/some/path",
                wrapper.parseRepoUrl("git+user@git.example.com:/some/path#some-branch"));

    }

    @Test
    public void testParseBranchName() {
        assertEquals("c234234", FogbugzEnvironmentWrapper.parseBranchName("anatoly/some#c234234"));
        assertEquals("c234234-2", FogbugzEnvironmentWrapper.parseBranchName("anatoly/some#c234234-2"));
        assertEquals("c234234_2", FogbugzEnvironmentWrapper.parseBranchName("anatoly/some#c234234_2"));
    }
}

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
                "ssh://code.example.com//var/hg/users/anatoly#test",
                wrapper.parseRepoUrl("anatoly#test"));

        assertEquals(
                "ssh://code.example.com//var/hg/users/something/other#test",
                wrapper.parseRepoUrl("something/other#test"));

        assertEquals(
                "ssh://another.code.example.com//var/something#other",
                wrapper.parseRepoUrl("ssh://another.code.example.com//var/something#other"));

        assertEquals(
                "ssh://another.code.example.com//var/something#other",
                wrapper.parseRepoUrl("hg+ssh://another.code.example.com//var/something#other"));

        assertEquals(
                "ssh://code.example.com//var/hg/unstable/paylogic_pta#pta1404",
                wrapper.parseRepoUrl("/var/hg/unstable/paylogic_pta#pta1404"));

        assertEquals(
                "ssh://code.example.com//var/something/test#pta1404",
                wrapper.parseRepoUrl("/var/something/test#pta1404"));

        wrapper = new FogbugzEnvironmentWrapper(true, "https://code.example.com/var/hg/users/");
        assertEquals(
                "https://code.example.com/var/something/test#pta1404",
                wrapper.parseRepoUrl("git+https://code.example.com/var/something/test#pta1404"));

    }

    @Test
    public void testParseBranchName() {
        assertEquals("c234234", FogbugzEnvironmentWrapper.parseBranchName("anatoly/some#c234234"));
        assertEquals("c234234-2", FogbugzEnvironmentWrapper.parseBranchName("anatoly/some#c234234-2"));
        assertEquals("c234234_2", FogbugzEnvironmentWrapper.parseBranchName("anatoly/some#c234234_2"));
    }
}

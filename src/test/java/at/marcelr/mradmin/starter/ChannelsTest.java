package at.marcelr.mradmin.starter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelsTest {

    private static final String DL = "https://github.com/therealmarci/mradmin/releases/download/";
    private static final String SHA = "a".repeat(64);

    private static String release(String tag, String asset, String url, String digest) {
        return """
                {"tag_name":"%s","draft":false,"assets":[{"name":"%s","browser_download_url":"%s","digest":"%s"},
                 {"name":"mrserver.sh","browser_download_url":"%smrserver.sh","digest":"sha256:%s"}]}
                """.formatted(tag, asset, url, digest, DL, SHA);
    }

    private static Object releases(String... items) {
        return Json.parse("[" + String.join(",", items) + "]");
    }

    @Test
    void betaTakesTheNewestReleaseOfThisMinecraftVersion() {
        Object list = releases(
                release("mc26.3-BET-1.4.1-bu002", "MRadmin-mc26.3-BET-1.4.1-bu002.jar",
                        DL + "mc26.3-BET-1.4.1-bu002/MRadmin-mc26.3-BET-1.4.1-bu002.jar", "sha256:" + SHA),
                release("mc26.4-BET-0.1.0-bu001", "MRadmin-mc26.4-BET-0.1.0-bu001.jar",
                        DL + "mc26.4-BET-0.1.0-bu001/MRadmin-mc26.4-BET-0.1.0-bu001.jar", "sha256:" + SHA),
                release("mc0-dev", "MRadmin-dev.jar.mrdev", DL + "mc0-dev/MRadmin-dev.jar.mrdev", "sha256:" + SHA),
                release("mc26.3-STA-1.4.0-bu001", "MRadmin-mc26.3-STA-1.4.0-bu001.jar",
                        DL + "mc26.3-STA-1.4.0-bu001/MRadmin-mc26.3-STA-1.4.0-bu001.jar", "sha256:" + SHA),
                release("mc26.3-v1.4.0", "MRadmin-mc26.3-v1.4.0.jar", DL + "mc26.3-v1.4.0/MRadmin-mc26.3-v1.4.0.jar",
                        "sha256:" + SHA));
        Channels.Pick pick = Channels.pickBeta(list, "26.3", "1.4.0");
        assertEquals("BET-1.4.1-bu002", pick.label());
        assertTrue(pick.url().endsWith("/MRadmin-mc26.3-BET-1.4.1-bu002.jar"));
        assertEquals(SHA, pick.sha256());
        assertFalse(pick.dev());
        // The running MRadmin is already the newest: keep it.
        assertNull(Channels.pickBeta(list, "26.3", "BET-1.4.1-bu002"));
        // A stable newer than the installed one counts too (beta gets the stables).
        Object onlyStable = releases(release("mc26.3-STA-1.4.0-bu001", "MRadmin-mc26.3-STA-1.4.0-bu001.jar",
                DL + "mc26.3-STA-1.4.0-bu001/MRadmin-mc26.3-STA-1.4.0-bu001.jar", "sha256:" + SHA));
        assertEquals("STA-1.4.0-bu001", Channels.pickBeta(onlyStable, "26.3", "1.4.0").label());
    }

    @Test
    void betaIgnoresForeignOrUncheckedDownloads() {
        assertNull(Channels.pickBeta(releases(release("mc26.3-BET-1.5.0-bu001", "MRadmin-mc26.3-BET-1.5.0-bu001.jar",
                "https://evil.example/MRadmin.jar", "sha256:" + SHA)), "26.3", "1.4.0"));
        assertNull(Channels.pickBeta(releases(release("mc26.3-BET-1.5.0-bu001", "MRadmin-mc26.3-BET-1.5.0-bu001.jar",
                DL + "x/MRadmin.jar", "")), "26.3", "1.4.0"));
        assertNull(Channels.pickBeta(releases(release("mc26.3-BET-1.5.0-bu001", "Other.jar",
                DL + "x/Other.jar", "sha256:" + SHA)), "26.3", "1.4.0"));
    }

    @Test
    void devPicksTheMradminPluginAndChecksTheToken() throws Exception {
        String check = Base64.getEncoder().encodeToString(
                DevSeal.seal(Channels.CHECK_TEXT.getBytes(StandardCharsets.UTF_8), "geheim"));
        Object json = Json.parse("""
                {"format":1,"commit":"0123456789abcdef","shortCommit":"0123456","check":"%s","files":[
                 {"file":"MRsleep-dev.jar.mrdev","artifact":"MRsleep","part":"plugin","minecraft":"26.3","sha256":"%s"},
                 {"file":"MRadmin-dev.jar.mrdev","artifact":"MRadmin","part":"plugin","minecraft":"26.3","sha256":"%s"}]}
                """.formatted(check, SHA, "B".repeat(64)));
        Channels.Pick pick = Channels.pickDev(json, "26.3");
        assertEquals("dev 0123456", pick.label());
        assertEquals(DL + "mc0-dev/MRadmin-dev.jar.mrdev", pick.url());
        assertEquals("b".repeat(64), pick.sha256());
        assertTrue(pick.dev());
        assertNull(Channels.pickDev(json, "26.4"));
        assertTrue(Channels.tokenValid(json, "geheim"));
        assertFalse(Channels.tokenValid(json, "falsch"));
        assertNull(Channels.tokenValid(Json.parse("{\"format\":1}"), "geheim"));
    }

    @Test
    void channelOptionAndSeed(@TempDir Path dir) throws Exception {
        assertEquals("beta", Channels.normalize(" Beta "));
        assertNull(Channels.normalize("nightly"));

        StarterFiles files = new StarterFiles(dir);
        java.nio.file.Files.createDirectories(files.dir());
        Setup.seed(files, null, null, false, null, "beta", null);
        Properties seed = StarterFiles.read(files.seed());
        assertEquals("beta", seed.getProperty("versions.channel"));
        assertNull(seed.getProperty("versions.dev-token"));

        Setup.seed(files, null, null, false, null, "dev", " token ");
        seed = StarterFiles.read(files.seed());
        assertEquals("beta", seed.getProperty("versions.channel"));
        assertEquals("token", seed.getProperty("versions.dev-token"));
        assertEquals("dev", seed.getProperty(Setup.SEED_PLUGIN_CHANNEL + "MRadmin"));
        for (String key : seed.stringPropertyNames()) {
            assertTrue(Setup.SEED_KEYS.contains(key) || key.startsWith(Setup.SEED_PLUGIN_CHANNEL), key);
        }
    }
}

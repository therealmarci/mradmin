package at.marcelr.mradmin.starter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterLogicTest {

    private static final String BUILDS = """
            [{"id":244,"channel":"BETA","commits":[],"downloads":{"server:default":{"name":"paper-26.3-244.jar",
              "checksums":{"sha256":"AA"},"size":1,"url":"https://fill-data.papermc.io/v1/objects/aa/paper-26.3-244.jar"}}},
             {"id":243,"time":"2026-11-05T23:26:23Z","channel":"STABLE","commits":[{"sha":"7ef","message":"Add crafter recipes (#14429)\\n\\nmore"}],
              "downloads":{"server:default":{"name":"paper-26.3-243.jar","checksums":{"sha256":"650805B6"},"size":55279926,
              "url":"https://fill-data.papermc.io/v1/objects/650805b6/paper-26.3-243.jar"}}},
             {"id":242,"channel":"STABLE","commits":[],"downloads":{"server:default":{"name":"paper-26.3-242.jar",
              "checksums":{"sha256":"BB"},"size":1,"url":"https://evil.example/paper.jar"}}},
             {"id":200,"channel":"STABLE","commits":[],"downloads":{"server:default":{"name":"paper-26.3-200.jar",
              "checksums":{"sha256":"cc"},"size":1,"url":"https://fill-data.papermc.io/v1/objects/cc/paper-26.3-200.jar"}}}]
            """;

    @Test
    void jsonReadsNestedValues() {
        Object value = Json.parse("{\"a\":[1,2.5,\"x\\u00e4\\n\",true,null],\"b\":{\"c\":-3}}");
        Map<String, Object> map = Json.object(value);
        List<Object> a = Json.array(map.get("a"));
        assertEquals(1L, a.get(0));
        assertEquals(2.5, a.get(1));
        assertEquals("xä\n", a.get(2));
        assertEquals(Boolean.TRUE, a.get(3));
        assertNull(a.get(4));
        assertEquals(-3, Json.number(Json.object(map.get("b")).get("c"), 0));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1} x"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,"));
    }

    @Test
    void picksNewestStableBuildFromPaperMcOnly() {
        Paper.Build build = Paper.pickStable("26.3", Json.parse(BUILDS));
        assertNotNull(build);
        assertEquals(243, build.id());
        assertEquals("650805b6", build.sha256());
        assertEquals(List.of("Add crafter recipes (#14429)"), build.commits());
        assertNull(Paper.pickStable("26.3", Json.parse("[]")));
    }

    @Test
    void readsBuildNumberFromPaperVersion() {
        assertEquals(223, Paper.buildOf("26.3-223-main@e415f5e (2026-10-25T12:46:54Z)"));
        assertEquals(243, Paper.buildOf("26.3-243-7ef2f69 (MC: 26.3)"));
        assertEquals(-1, Paper.buildOf("git-Paper-abc"));
        assertEquals(-1, Paper.buildOf(null));
    }

    @Test
    void trustsOnlyPaperMcStorage() {
        assertTrue(Paper.trusted("https://fill-data.papermc.io/v1/objects/x/paper.jar"));
        assertFalse(Paper.trusted("http://fill-data.papermc.io/v1/objects/x/paper.jar"));
        assertFalse(Paper.trusted("https://fill-data.papermc.io.evil.example/paper.jar"));
        assertFalse(Paper.trusted("not a url"));
    }

    @Test
    void javaFlagsLetOnlyTuningThrough() {
        for (String ok : List.of("-XX:+UseG1GC", "-XX:G1HeapRegionSize=8M", "-XX:MaxGCPauseMillis=200",
                "-Dusing.aikars.flags=https://mcflags.emc.gs", "-Daikars.new.flags=true", "-Dfile.encoding=UTF-8",
                "-Djava.net.preferIPv4Stack=true", "-Xss2M", "--add-modules=jdk.incubator.vector")) {
            assertNull(JavaFlags.problem(ok), ok);
        }
        for (String bad : List.of("-XX:OnOutOfMemoryError=sh", "-XX:OnError=calc", "-javaagent:x.jar", "-agentlib:jdwp",
                "-agentpath:/x.so", "-XX:Flags=/tmp/f", "-XX:VMOptionsFile=/tmp/f", "-Dlog4j.configurationFile=http://x",
                "-Djava.library.path=/tmp", "-Djdk.attach.allowAttachSelf=true", "-cp", "@args.txt", "-Xmx8G",
                "-Dx=$(id)", "-XX:HeapDumpPath=/etc", "-jar")) {
            assertNotNull(JavaFlags.problem(bad), bad);
        }
        assertEquals(List.of("-XX:+UseG1GC"), JavaFlags.allowed(JavaFlags.split("  -XX:+UseG1GC   -javaagent:a.jar ")));
        assertEquals("4G", JavaFlags.memory(" 4g "));
        assertEquals("4096M", JavaFlags.memory("4096M"));
        assertNull(JavaFlags.memory("0G"));
        assertNull(JavaFlags.memory("4 G"));
    }

    @Test
    void eventLinesRoundTrip() {
        StarterFiles.Event e = new StarterFiles.Event(1730000000000L, "paper-update", "223 243");
        StarterFiles.Event back = StarterFiles.Event.parse(e.line());
        assertEquals(e, back);
        assertEquals(new StarterFiles.Event(5, "crash", ""), StarterFiles.Event.parse("5 crash"));
        assertNull(StarterFiles.Event.parse("garbage"));
        assertNull(StarterFiles.Event.parse("x crash"));
    }

    @Test
    void localSettingsKeepWindowsPathsAsWritten(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        StarterFiles files = new StarterFiles(dir);
        String javaPath = "C:\\Program Files\\Eclipse Adoptium\\jdk-25\\bin\\java.exe";
        new LocalSettings(javaPath, "paper.jar", "window", "minecraft-test", "-XX:+UseG1GC").save(files);
        String text = java.nio.file.Files.readString(files.local());
        assertTrue(text.contains("java=" + javaPath), text);
        LocalSettings back = LocalSettings.load(files);
        assertEquals(javaPath, back.java());
        assertEquals("window", back.mode());
        assertEquals("minecraft-test", back.name());
        assertEquals("-XX:+UseG1GC", back.extraFlags());
    }

    @Test
    void crashLinesOnlyFromPaperNotFromPlugins() {
        assertTrue(Supervisor.crashLine("[08:51:57 ERROR]: Encountered an unexpected exception"));
        assertTrue(Supervisor.crashLine("[08:51:57 ERROR]: This crash report has been saved to: crash-reports/x.txt"));
        assertTrue(Supervisor.crashLine("[08:51:57 ERROR]: The server has stopped responding! This is (probably) not a Paper bug."));
        assertFalse(Supervisor.crashLine("[08:52:27 WARN]: [MRadmin] The server crashed at 8:52 AM (exit code 1) - the starter started it again."));
        assertFalse(Supervisor.crashLine("[08:52:27 WARN]: [SomePlugin] Encountered an unexpected exception while saving"));
        assertFalse(Supervisor.crashLine("[08:52:42 INFO]: Stopping server"));
    }

    @Test
    void installMemoryIsCheckedAndSeeded(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        assertNull(Setup.memoryProblem("2G", "6G"));
        assertNull(Setup.memoryProblem(null, "4096M"));
        assertNull(Setup.memoryProblem("4G", "4096M"));
        assertNotNull(Setup.memoryProblem("8G", "4G"));
        assertNotNull(Setup.memoryProblem("2 GB", null));
        assertNotNull(Setup.memoryProblem(null, "lots"));
        assertEquals(4096, Setup.megabytes("4G"));
        assertEquals(1024, Setup.megabytes("1048576K"));

        assertNull(Setup.webUrlProblem(null));
        assertNull(Setup.webUrlProblem("https://admin.example.com"));
        assertNull(Setup.webUrlProblem("http://10.0.0.5:8125/mradmin"));
        assertNotNull(Setup.webUrlProblem("admin.example.com"));
        assertNotNull(Setup.webUrlProblem("https://x.com/\"><script>"));

        StarterFiles files = new StarterFiles(dir);
        Setup.seed(files, "2G", "6G", true, "https://admin.example.com");
        java.util.Properties launch = StarterFiles.read(files.launch());
        assertEquals("2G", launch.getProperty("memory-min"));
        assertEquals("6G", launch.getProperty("memory-max"));
        // The starter uses it right away ...
        assertEquals("6G", Supervisor.Launch.read(files).memoryMax());
        // ... and MRadmin takes these over into config.yml on its first start - only allowed keys.
        java.util.Properties seed = StarterFiles.read(files.seed());
        assertEquals("6G", seed.getProperty("server.memory-max"));
        assertEquals("true", seed.getProperty("web.enabled"));
        assertEquals("sqlite", seed.getProperty("storage.type"));
        assertEquals("https://admin.example.com", seed.getProperty("web.public-url"));
        assertTrue(Setup.SEED_KEYS.containsAll(seed.stringPropertyNames()));
    }
}

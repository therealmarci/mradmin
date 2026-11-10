package at.marcelr.mradmin.versions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DevJsonTest {

    private static final String SHA = "a".repeat(64);
    private static final String JSON = """
            {"format": 1, "repository": "therealmarci/mrtoolbar", "commit": "0123456789abcdef0123456789abcdef01234567",
             "shortCommit": "0123456", "run": 42, "date": "2026-11-07T08:10:00Z",
             "files": [
               {"file": "MRtoolbar-dev.jar.mrdev", "artifact": "MRtoolbar", "part": "plugin", "baseVersion": "STA-1.3.2-bu001",
                "minecraft": "26.3", "jar": "MRtoolbar-mc26.3-v1.3.2.jar", "sha256": "%s", "size": 1234},
               {"file": "MRtoolbar-mod-dev.jar.mrdev", "artifact": "MRtoolbar-mod", "part": "mod", "baseVersion": "1.0.0",
                "minecraft": "26.3", "jar": "x.jar", "sha256": "%s", "size": 99}
             ]}
            """.formatted(SHA, SHA);

    @Test
    void picksThePluginPart() {
        VersionManager.DevBuild dev = DevJson.pick(JSON, "MRtoolbar", "26.3");
        assertEquals("MRtoolbar-dev.jar.mrdev", dev.file());
        assertEquals("0123456", dev.shortCommit());
        assertEquals(42, dev.run());
        assertEquals("STA-1.3.2-bu001", dev.base());
        assertEquals(1234, dev.size());
        assertEquals(SHA, dev.sha256());
    }

    @Test
    void tokenCheck() throws Exception {
        String check = java.util.Base64.getEncoder().encodeToString(at.marcelr.mradmin.starter.DevSeal.seal(
                DevJson.CHECK_TEXT.getBytes(java.nio.charset.StandardCharsets.UTF_8), "right"));
        String json = JSON.replace("\"run\": 42,", "\"run\": 42, \"check\": \"" + check + "\",");
        assertEquals(Boolean.TRUE, DevJson.tokenValid(json, "right"));
        assertEquals(Boolean.FALSE, DevJson.tokenValid(json, "wrong"));
        assertNull(DevJson.tokenValid(json, ""));
        assertNull(DevJson.tokenValid(JSON, "right"));   // older dev.json without check
    }

    @Test
    void nothingForOtherPluginsMinecraftOrFormats() {
        assertNull(DevJson.pick(JSON, "MRsleep", "26.3"));
        assertNull(DevJson.pick(JSON, "MRtoolbar", "26.4"));
        assertNull(DevJson.pick(JSON.replace("\"format\": 1", "\"format\": 2"), "MRtoolbar", "26.3"));
        assertNull(DevJson.pick(JSON.replace("MRtoolbar-dev.jar.mrdev", "../evil.mrdev"), "MRtoolbar", "26.3"));
        assertNull(DevJson.pick(JSON.replace("0123456789abcdef0123456789abcdef01234567", "not-a-commit"), "MRtoolbar", "26.3"));
    }
}

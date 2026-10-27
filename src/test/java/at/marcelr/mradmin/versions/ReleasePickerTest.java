package at.marcelr.mradmin.versions;

import at.marcelr.mradmin.versions.ReleasePicker.Auto;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleasePickerTest {

    private static String release(String tag, boolean pre, String... assets) {
        StringBuilder a = new StringBuilder();
        for (String asset : assets) {
            if (!a.isEmpty()) {
                a.append(',');
            }
            a.append("{\"name\":\"").append(asset).append("\",\"browser_download_url\":\"https://example/").append(asset)
                    .append("\",\"size\":10,\"digest\":\"sha256:ABC\"}");
        }
        return "{\"tag_name\":\"" + tag + "\",\"prerelease\":" + pre + ",\"draft\":false,\"html_url\":\"https://example/"
                + tag + "\",\"body\":\"notes\",\"published_at\":\"2026-10-27T10:00:00Z\",\"assets\":[" + a + "]}";
    }

    private static JsonArray list(String... releases) {
        return JsonParser.parseString("[" + String.join(",", releases) + "]").getAsJsonArray();
    }

    @Test
    void newestStableReleaseForThisMinecraftVersion() {
        JsonArray releases = list(
                release("mc26.3-v1.1.5", false, "MRsleep-mc26.3-v1.1.5.jar"),
                release("mc26.3-v1.2.0", false, "MRsleep-mc26.3-v1.2.0.jar"),
                release("mc26.4-v1.3.0", false, "MRsleep-mc26.4-v1.3.0.jar"));
        ReleasePicker.Release r = ReleasePicker.pick(releases, "MRsleep", "26.3", false, false);
        assertEquals("1.2.0", r.version());
        assertEquals("MRsleep-mc26.3-v1.2.0.jar", r.asset());
        assertEquals("abc", r.sha256());
    }

    @Test
    void preReleasesOnlyWhenWanted() {
        JsonArray releases = list(
                release("mc26.3-v0.6.0", true, "MRadmin-mc26.3-v0.6.0.jar"),
                release("mc26.3-v0.5.0", true, "MRadmin-mc26.3-v0.5.0.jar"));
        assertNull(ReleasePicker.pick(releases, "MRadmin", "26.3", false, false));
        assertEquals("0.6.0", ReleasePicker.pick(releases, "MRadmin", "26.3", false, true).version());
    }

    @Test
    void pluginAndModReleasesInOneRepository() {
        JsonArray releases = list(
                release("mod-mc26.3-v0.2.1", true, "MRtoolbar-mod-mc26.3-v0.2.1.jar"),
                release("plugin-mc26.3-v0.3.0", true, "MRtoolbar-mc26.3-v0.3.0.jar"));
        assertEquals("0.3.0", ReleasePicker.pick(releases, "MRtoolbar", "26.3", false, true).version());
        assertEquals("0.2.1", ReleasePicker.pick(releases, "MRtoolbar", "26.3", true, true).version());
    }

    @Test
    void noChecksumWithoutDigest() {
        JsonArray releases = JsonParser.parseString("[{\"tag_name\":\"mc26.3-v1.0.0\",\"prerelease\":false,\"assets\":"
                + "[{\"name\":\"MRsleep-mc26.3-v1.0.0.jar\",\"browser_download_url\":\"x\"}]}]").getAsJsonArray();
        assertNull(ReleasePicker.pick(releases, "MRsleep", "26.3", false, false).sha256());
    }

    @Test
    void autoDownloadLevels() {
        assertTrue(ReleasePicker.autoAllowed(Auto.PATCH, "1.2.0", "1.2.1"));
        assertFalse(ReleasePicker.autoAllowed(Auto.PATCH, "1.2.0", "1.3.0"));
        assertTrue(ReleasePicker.autoAllowed(Auto.MINOR, "1.2.0", "1.3.0"));
        assertFalse(ReleasePicker.autoAllowed(Auto.MINOR, "1.2.0", "2.0.0"));
        assertTrue(ReleasePicker.autoAllowed(Auto.ALL, "1.2.0", "2.0.0"));
        assertFalse(ReleasePicker.autoAllowed(Auto.OFF, "1.2.0", "1.2.1"));
        assertFalse(ReleasePicker.autoAllowed(Auto.ALL, "1.2.0", "1.2.0"));
        assertTrue(ReleasePicker.autoAllowed(Auto.PATCH, "0.6.0+mc26.3", "0.6.1"));
    }
}

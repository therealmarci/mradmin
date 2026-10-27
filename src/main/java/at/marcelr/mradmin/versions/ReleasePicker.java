package at.marcelr.mradmin.versions;

import at.marcelr.mradmin.util.Versions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks the newest fitting release of an MRtechnology plugin from the GitHub releases list. Pure logic, no server
 * access. A release fits if it has a JAR named like the plugin for this Minecraft version
 * ({@code MRsleep-mc26.3-v1.2.0.jar}; the MRtoolbar mod: {@code MRtoolbar-mod-mc26.3-v0.2.1.jar}) - the tag does not
 * matter, so repositories with plugin and mod releases ({@code plugin-mc...}, {@code mod-mc...}) work too.
 */
public final class ReleasePicker {

    private ReleasePicker() {
    }

    /** What {@code versions.auto-download} allows to download by itself. */
    public enum Auto { OFF, PATCH, MINOR, ALL }

    /**
     * A fitting release.
     *
     * @param version plugin version without prefix, e.g. "1.2.0"
     * @param sha256  checksum GitHub keeps for the JAR (hex), null if there is none
     * @param notes   release text (Markdown)
     */
    public record Release(String tag, String version, boolean prerelease, String page, String asset, String download,
                          long size, String sha256, String notes, long published) {
    }

    /**
     * @param name        plugin name, e.g. "MRsleep"
     * @param mod         the client mod instead of the plugin
     * @param prereleases also beta/alpha releases
     * @return the newest fitting release, or null
     */
    public static Release pick(JsonArray releases, String name, String minecraft, boolean mod, boolean prereleases) {
        Pattern jar = Pattern.compile("^" + Pattern.quote(name) + (mod ? "-mod" : "") + "-mc" + Pattern.quote(minecraft)
                + "-v(.+)\\.jar$", Pattern.CASE_INSENSITIVE);
        Release best = null;
        for (JsonElement element : releases) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject release = element.getAsJsonObject();
            boolean prerelease = bool(release, "prerelease");
            if (bool(release, "draft") || (prerelease && !prereleases) || !release.has("assets")) {
                continue;
            }
            for (JsonElement a : release.getAsJsonArray("assets")) {
                JsonObject asset = a.getAsJsonObject();
                Matcher m = jar.matcher(string(asset, "name"));
                if (!m.matches()) {
                    continue;
                }
                String version = m.group(1);
                if (best == null || Versions.compare(version, best.version()) > 0) {
                    String digest = string(asset, "digest");
                    best = new Release(string(release, "tag_name"), version, prerelease, string(release, "html_url"),
                            string(asset, "name"), string(asset, "browser_download_url"),
                            asset.has("size") ? asset.get("size").getAsLong() : -1,
                            digest.toLowerCase(Locale.ROOT).startsWith("sha256:") ? digest.substring(7).toLowerCase(Locale.ROOT) : null,
                            string(release, "body"), published(string(release, "published_at")));
                }
            }
        }
        return best;
    }

    /** Whether {@code mode} allows downloading {@code latest} by itself while {@code installed} runs. */
    public static boolean autoAllowed(Auto mode, String installed, String latest) {
        if (Versions.compare(latest, installed) <= 0) {
            return false;
        }
        int[] a = core(installed);
        int[] b = core(latest);
        return switch (mode) {
            case OFF -> false;
            case ALL -> true;
            case MINOR -> a[0] == b[0];
            case PATCH -> a[0] == b[0] && a[1] == b[1];
        };
    }

    /** MAJOR, MINOR of "1.2.3", "v1.2.3-rc.1" or "1.2.3+mc26.3"; missing parts are 0. */
    private static int[] core(String version) {
        String plain = Versions.plugin(version).split("[-+]", 2)[0];
        String[] parts = plain.split("\\.");
        int[] result = new int[2];
        for (int i = 0; i < 2 && i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }

    private static boolean bool(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    private static long published(String raw) {
        try {
            return raw.isEmpty() ? 0 : Instant.parse(raw).toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}

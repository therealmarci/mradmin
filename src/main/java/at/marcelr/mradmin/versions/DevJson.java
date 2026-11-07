package at.marcelr.mradmin.versions;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;

/**
 * Reads the dev.json of the dev channel (written by .github/dev-channel.sh of each plugin repository) and picks the
 * build of one plugin. Pure logic, no server access.
 * <pre>
 * {"format": 1, "commit": "…", "shortCommit": "abc1234", "run": 42, "date": "2026-11-07T08:10:00Z",
 *  "files": [{"file": "MRsleep-dev.jar.mrdev", "artifact": "MRsleep", "part": "plugin", "baseVersion": "STA-1.4.2-bu001",
 *             "minecraft": "26.3", "jar": "MRsleep-mc26.3-….jar", "sha256": "…", "size": 177501}]}
 * </pre>
 */
public final class DevJson {

    private DevJson() {
    }

    /**
     * The plugin part named {@code name} for this Minecraft version, or null (no such part, other Minecraft version or an
     * unknown format).
     */
    public static VersionManager.DevBuild pick(String json, String name, String minecraft) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (number(root, "format") != 1 || !root.has("files")) {
            return null;
        }
        String commit = string(root, "commit");
        if (!commit.matches("[0-9a-f]{7,40}")) {
            return null;
        }
        for (JsonElement el : root.getAsJsonArray("files")) {
            JsonObject f = el.getAsJsonObject();
            if (!"plugin".equals(string(f, "part")) || !name.equals(string(f, "artifact"))) {
                continue;
            }
            String file = string(f, "file");
            String sha = string(f, "sha256");
            if (!file.matches("[A-Za-z0-9_.-]+\\.mrdev") || !sha.matches("[0-9a-fA-F]{64}")
                    || !minecraft.equals(string(f, "minecraft"))) {
                return null;
            }
            long date;
            try {
                date = Instant.parse(string(root, "date")).toEpochMilli();
            } catch (RuntimeException e) {
                date = 0;
            }
            String shortCommit = string(root, "shortCommit");
            return new VersionManager.DevBuild(commit, shortCommit.isEmpty() ? commit.substring(0, 7) : shortCommit, date,
                    (int) number(root, "run"), file, sha.toLowerCase(java.util.Locale.ROOT), number(f, "size"),
                    string(f, "baseVersion"), minecraft);
        }
        return null;
    }

    /** The known text dev-channel.sh seals into "check". */
    static final String CHECK_TEXT = "mrtechnology-dev-token-check";

    /**
     * Whether {@code token} is the release token of this dev channel: true if the sealed "check" opens with it, false if
     * not, null if the dev.json has no check (older builds) or no token is given.
     */
    public static Boolean tokenValid(String json, String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String check = string(JsonParser.parseString(json).getAsJsonObject(), "check");
        if (check.isEmpty()) {
            return null;
        }
        try {
            byte[] plain = at.marcelr.mradmin.starter.DevSeal.open(java.util.Base64.getDecoder().decode(check), token);
            return CHECK_TEXT.equals(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | java.security.GeneralSecurityException e) {
            return false;
        }
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static long number(JsonObject o, String key) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsLong() : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

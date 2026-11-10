package at.marcelr.mradmin.versions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Keeps plugin updates from going backwards. Two rules, both hard - a blocked version is neither offered nor
 * downloaded, going back is only possible by hand:
 * <ol>
 *     <li><b>Migrations:</b> the new JAR must not have a lower {@code config-version} (config.yml) or
 *     {@code lang-version} (lang/de.yml, lang/en.yml) than the files on the server. An older plugin cannot undo a
 *     migration - it would run with renamed keys it does not know.</li>
 *     <li><b>Code:</b> if both commits are known, the new build must be a descendant of the running one (GitHub
 *     compare: "ahead"). "behind" = older, "diverged" = another line (e.g. a dev build with features the stable does not
 *     have yet). Dev builds carry the number of the last release, so the version number alone cannot tell.</li>
 * </ol>
 * Pure logic apart from reading the files.
 */
public final class DowngradeGuard {

    /** Why a version is blocked: reason "config", "lang" or "code"; have/offered = the numbers or commit states. */
    public record Block(String reason, String have, String offered) {
    }

    private static final String[] LANG_FILES = {"lang/de.yml", "lang/en.yml"};

    private DowngradeGuard() {
    }

    /**
     * Rule 1: compares the new JAR with the plugin's data folder.
     *
     * @return the block, or null if the JAR may be installed (also if a number is missing on either side)
     */
    public static Block migrations(File jar, File dataFolder) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            Block config = compare("config", read(zip, "config.yml"), file(dataFolder, "config.yml"), "config-version");
            if (config != null) {
                return config;
            }
            for (String lang : LANG_FILES) {
                Block block = compare("lang", read(zip, lang), file(dataFolder, lang), "lang-version");
                if (block != null) {
                    return block;
                }
            }
        }
        return null;
    }

    private static Block compare(String reason, String inJar, String onServer, String key) {
        Integer offered = number(inJar, key);
        Integer have = number(onServer, key);
        return offered != null && have != null && offered < have ? new Block(reason, have.toString(), offered.toString()) : null;
    }

    /**
     * Rule 2: the status of GitHub's compare (base = running commit, head = new build).
     *
     * @return the block, or null if the new build is newer ("ahead") or the status is unknown
     */
    public static Block code(String status, String runningCommit, String newCommit) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "behind", "diverged" -> new Block("code", shortOf(runningCommit), shortOf(newCommit) + " (" + status + ")");
            default -> null;
        };
    }

    /** "config-version: 51" -> 51 (also quoted); null if missing or the text is null. */
    static Integer number(String text, String key) {
        if (text == null) {
            return null;
        }
        Matcher m = Pattern.compile("(?m)^" + Pattern.quote(key) + ":\\s*['\"]?(\\d{1,9})").matcher(text.replace("\uFEFF", ""));
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private static String read(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readNBytes(1_000_000), StandardCharsets.UTF_8);
        }
    }

    private static String file(File folder, String name) throws IOException {
        File f = new File(folder, name);
        return f.isFile() ? Files.readString(f.toPath(), StandardCharsets.UTF_8) : null;
    }

    private static String shortOf(String commit) {
        return commit == null ? "?" : commit.substring(0, Math.min(7, commit.length()));
    }
}

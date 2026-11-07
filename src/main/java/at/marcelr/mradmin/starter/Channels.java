package at.marcelr.mradmin.starter;

import at.marcelr.mradmin.util.Versions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * The release channel of a new installation ({@code mrserver setup --channel stable|beta|dev}): finds the MRadmin that
 * belongs to it - the newest beta if it is newer than the running (stable) MRadmin, or the newest dev build, which
 * only opens with the release token of the dev channel. No Bukkit types. Every download must match its SHA-256.
 */
public final class Channels {

    public static final String REPO = "therealmarci/mradmin";
    private static final String DOWNLOADS = "https://github.com/" + REPO + "/releases/download/";
    /** Rolling release of the dev channel (Minecraft "0", so no update check ever takes it for a release). */
    static final String DEV_TAG = "mc0-dev";
    /** The text .github/dev-channel.sh seals into "check" of the dev.json (same as DevJson.CHECK_TEXT). */
    static final String CHECK_TEXT = "mrtechnology-dev-token-check";

    /** An MRadmin to install instead of the running one. {@code dev} = sealed dev build. */
    public record Pick(String label, String url, String sha256, boolean dev) {
    }

    private Channels() {
    }

    /** "stable", "beta" or "dev" - null if unknown. */
    public static String normalize(String channel) {
        if (channel == null) {
            return null;
        }
        String c = channel.trim().toLowerCase(Locale.ROOT);
        return c.matches("stable|beta|dev") ? c : null;
    }

    // ------------------------------------------------------------------ pure logic

    /**
     * The newest MRadmin release for this Minecraft version (stable or beta - a beta installation gets both), if it is
     * newer than {@code installed}; null otherwise.
     *
     * @param releases the release list of the GitHub API
     */
    static Pick pickBeta(Object releases, String minecraft, String installed) {
        Pick best = null;
        String bestTag = installed;
        for (Object o : Json.array(releases)) {
            Map<String, Object> r = Json.object(o);
            String tag = Json.string(r.get("tag_name"));
            if (tag == null || Boolean.TRUE.equals(r.get("draft")) || !minecraft.equals(Versions.minecraft(tag))
                    || Versions.compare(tag, bestTag) <= 0) {
                continue;
            }
            for (Object a : Json.array(r.get("assets"))) {
                Map<String, Object> asset = Json.object(a);
                String name = Json.string(asset.get("name"));
                String url = Json.string(asset.get("browser_download_url"));
                String digest = Json.string(asset.get("digest"));
                if (name == null || url == null || digest == null || !name.matches("MRadmin-mc[A-Za-z0-9.+-]*\\.jar")
                        || !url.startsWith(DOWNLOADS) || !digest.matches("sha256:[0-9a-fA-F]{64}")) {
                    continue;
                }
                best = new Pick(Versions.plugin(tag), url, digest.substring(7).toLowerCase(Locale.ROOT), false);
                bestTag = tag;
                break;
            }
        }
        return best;
    }

    /** The MRadmin build in a dev.json for this Minecraft version, null if there is none. */
    static Pick pickDev(Object devJson, String minecraft) {
        Map<String, Object> root = Json.object(devJson);
        if (Json.number(root.get("format"), 0) != 1) {
            return null;
        }
        String commit = Json.string(root.get("shortCommit"));
        for (Object o : Json.array(root.get("files"))) {
            Map<String, Object> f = Json.object(o);
            String file = Json.string(f.get("file"));
            String sha = Json.string(f.get("sha256"));
            if (!"plugin".equals(Json.string(f.get("part"))) || !"MRadmin".equals(Json.string(f.get("artifact")))
                    || !minecraft.equals(Json.string(f.get("minecraft"))) || file == null || sha == null
                    || !file.matches("[A-Za-z0-9_.-]+\\.mrdev") || !sha.matches("[0-9a-fA-F]{64}")) {
                continue;
            }
            return new Pick("dev " + (commit == null ? "?" : commit), DOWNLOADS + DEV_TAG + "/" + file,
                    sha.toLowerCase(Locale.ROOT), true);
        }
        return null;
    }

    /** true = the token opens the token check of the dev.json, false = it does not, null = the dev.json has no check. */
    static Boolean tokenValid(Object devJson, String token) {
        String check = Json.string(Json.object(devJson).get("check"));
        if (check == null || check.isEmpty()) {
            return null;
        }
        try {
            byte[] plain = DevSeal.open(Base64.getDecoder().decode(check), token);
            return CHECK_TEXT.equals(new String(plain, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ network

    /** Why GitHub's API said no, for the user. */
    static String github(int status) {
        return status == 403 || status == 429
                ? "GitHub allows 60 requests per hour without login and this address used them up - try again in an"
                        + " hour or set GITHUB_TOKEN"
                : "GitHub answered " + status;
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    private static HttpResponse<byte[]> get(String url) throws IOException, InterruptedException {
        return client().send(request(url).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    /** A GET to GitHub; with GITHUB_TOKEN in the environment, requests to its API count against that account. */
    static HttpRequest.Builder request(String url) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3))
                .header("User-Agent", Main.userAgent()).header("Accept", "application/vnd.github+json").GET();
        // Optional, for addresses that share GitHub's limit of 60 requests per hour: only sent to GitHub's API.
        String token = System.getenv("GITHUB_TOKEN");
        if (token != null && !token.isBlank() && url.startsWith("https://api.github.com/")) {
            request.header("Authorization", "Bearer " + token.trim());
        }
        return request;
    }

    /**
     * What to install for {@code channel}. Checks the release token before anything is changed.
     *
     * @return null = keep the running MRadmin (stable, or no newer beta)
     * @throws IOException with a message for the user (no dev build, wrong token, GitHub not reachable)
     */
    public static Pick resolve(String channel, String token) throws IOException, InterruptedException {
        String minecraft = Main.minecraft();
        if ("beta".equals(channel)) {
            HttpResponse<byte[]> r = get("https://api.github.com/repos/" + REPO + "/releases?per_page=50");
            if (r.statusCode() != 200) {
                throw new IOException("Could not look up the MRadmin betas (" + github(r.statusCode()) + ").");
            }
            return pickBeta(Json.parse(new String(r.body(), StandardCharsets.UTF_8)), minecraft, Main.version());
        }
        if ("dev".equals(channel)) {
            if (token == null || token.isBlank()) {
                throw new IOException("The dev channel needs the release token (environment variable MRSERVER_DEV_TOKEN).");
            }
            HttpResponse<byte[]> r = get(DOWNLOADS + DEV_TAG + "/dev.json");
            if (r.statusCode() != 200) {
                throw new IOException("There is no MRadmin dev build (GitHub answered " + r.statusCode() + ").");
            }
            Object json = Json.parse(new String(r.body(), StandardCharsets.UTF_8));
            if (!Boolean.TRUE.equals(tokenValid(json, token.trim()))) {
                throw new IOException("The release token is wrong - nothing was installed.");
            }
            Pick pick = pickDev(json, minecraft);
            if (pick == null) {
                throw new IOException("The dev channel has no MRadmin build for Minecraft " + minecraft + ".");
            }
            return pick;
        }
        return null;
    }

    /**
     * Downloads the pick (opens a dev build with the token), checks SHA-256 and that it is MRadmin, and saves it as
     * {@code target}. Nothing is left behind if a check fails.
     */
    public static void download(Pick pick, String token, Path target) throws IOException, InterruptedException {
        HttpResponse<byte[]> r = get(pick.url());
        if (r.statusCode() != 200) {
            throw new IOException("Download failed: HTTP " + r.statusCode());
        }
        byte[] jar = r.body();
        if (pick.dev()) {
            try {
                jar = DevSeal.open(jar, token.trim());
            } catch (GeneralSecurityException e) {
                throw new IOException("Could not open the dev build: " + e.getMessage());
            }
        }
        String sha = HexFormat.of().formatHex(Paper.sha256().digest(jar));
        if (!sha.equals(pick.sha256())) {
            throw new IOException("The download does not match its checksum - not using it.");
        }
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".download");
        Files.write(tmp, jar);
        try {
            if (!"MRadmin".equals(pluginName(tmp))) {
                throw new IOException("The download is not MRadmin - not using it.");
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** "name" of the plugin.yml in a JAR, null if there is none. */
    static String pluginName(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("plugin.yml");
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                    if (line.startsWith("name:")) {
                        return line.substring(5).trim().replaceAll("^['\"]|['\"]$", "");
                    }
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }
}

package at.marcelr.mradmin.starter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Paper builds from PaperMC's official download service (Fill, {@code fill.papermc.io/v3}). Only builds of the
 * channel STABLE and of exactly the given Minecraft version - a new Minecraft version is never chosen here. Every
 * download must match the SHA-256 checksum Fill gives. No Bukkit types.
 */
public final class Paper {

    public static final String API = "https://fill.papermc.io/v3/projects/paper";
    /** Downloads only from PaperMC's own storage. */
    private static final String DOWNLOAD_HOST = "fill-data.papermc.io";
    private static final Pattern BUILD_IN_VERSION = Pattern.compile("^[0-9][^-\\s]*-(\\d+)-");

    /**
     * @param commits first lines of the commit messages, newest first
     */
    public record Build(String minecraft, int id, String file, String url, String sha256, long size, String time,
                        List<String> commits) {
    }

    private final HttpClient client;
    private final String userAgent;

    public Paper(String userAgent) {
        this.userAgent = userAgent;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /** The build number in a Paper version text such as {@code 26.3-243-7ef2f69 (MC: 26.3)}, -1 if unknown. */
    public static int buildOf(String version) {
        if (version == null) {
            return -1;
        }
        Matcher m = BUILD_IN_VERSION.matcher(version.trim());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** The newest STABLE build in Fill's build list (pure logic), null if there is none. */
    public static Build pickStable(String minecraft, Object builds) {
        Build best = null;
        for (Object o : Json.array(builds)) {
            Map<String, Object> b = Json.object(o);
            if (!"STABLE".equals(Json.string(b.get("channel")))) {
                continue;
            }
            Map<String, Object> d = Json.object(Json.object(b.get("downloads")).get("server:default"));
            String url = Json.string(d.get("url"));
            String sha = Json.string(Json.object(d.get("checksums")).get("sha256"));
            String file = Json.string(d.get("name"));
            if (url == null || sha == null || file == null || !trusted(url) || !file.matches("[A-Za-z0-9._+-]+\\.jar")) {
                continue;
            }
            int id = (int) Json.number(b.get("id"), -1);
            if (best != null && best.id() >= id) {
                continue;
            }
            List<String> commits = new ArrayList<>();
            for (Object c : Json.array(b.get("commits"))) {
                String msg = Json.string(Json.object(c).get("message"));
                if (msg != null && !msg.isBlank()) {
                    commits.add(msg.lines().findFirst().orElse("").trim());
                }
            }
            best = new Build(minecraft, id, file, url, sha.toLowerCase(Locale.ROOT), Json.number(d.get("size"), -1),
                    Json.string(b.get("time")), List.copyOf(commits));
        }
        return best;
    }

    static boolean trusted(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && DOWNLOAD_HOST.equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** @return the newest STABLE build of this Minecraft version, null if Fill knows none */
    public Build latestStable(String minecraft) throws IOException, InterruptedException {
        if (!minecraft.matches("[0-9][0-9A-Za-z.\\-]*")) {
            throw new IOException("Invalid Minecraft version: " + minecraft);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + "/versions/" + minecraft + "/builds"))
                .timeout(Duration.ofSeconds(20)).header("User-Agent", userAgent).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new IOException("PaperMC answered HTTP " + response.statusCode());
        }
        return pickStable(minecraft, Json.parse(response.body()));
    }

    /** Downloads the build to {@code target} - only if the checksum matches, otherwise nothing is left behind. */
    public void download(Build build, Path target) throws IOException, InterruptedException {
        if (!trusted(build.url())) {
            throw new IOException("Download address is not PaperMC's: " + build.url());
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(build.url())).timeout(Duration.ofMinutes(5))
                .header("User-Agent", userAgent).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Download failed: HTTP " + response.statusCode());
        }
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".download");
        MessageDigest digest = sha256();
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                digest.update(buffer, 0, n);
                out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        String sha = HexFormat.of().formatHex(digest.digest());
        if (!sha.equals(build.sha256())) {
            Files.deleteIfExists(tmp);
            throw new IOException("Checksum does not match (expected " + build.sha256() + ", got " + sha + ")");
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** SHA-256 of a file as lower-case hex. */
    public static String sha256Of(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                digest.update(buffer, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

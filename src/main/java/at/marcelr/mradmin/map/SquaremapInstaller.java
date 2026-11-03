package at.marcelr.mradmin.map;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.util.Versions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.zip.ZipFile;

/**
 * Keeps squaremap installed and up to date, so the map of the player page works without anyone touching it: the
 * official squaremap release for this Minecraft version from Modrinth (project "squaremap", loader paper), only if
 * its SHA-512 matches what Modrinth publishes and the file really is squaremap. Missing → into {@code plugins/}
 * (active after the next start); newer → into {@code plugins/update/} under the installed JAR's name (Paper swaps it
 * on the next start). Nothing is replaced while the server runs. Off with {@code map.squaremap.auto-install} /
 * {@code auto-update}.
 */
final class SquaremapInstaller {

    private static final String API = "https://api.modrinth.com/v2/project/squaremap/version";
    private static final String CDN = "https://cdn.modrinth.com/";
    private static final long MAX_DOWNLOAD = 64L * 1024 * 1024;

    private final MRAdmin plugin;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    /** Version downloaded in this run (waiting for the next start), or null. */
    private volatile String staged;
    private volatile String lastError = "";

    SquaremapInstaller(MRAdmin plugin) {
        this.plugin = plugin;
    }

    /** What happened last: the version waiting for a restart, or the last error ("" = nothing to tell). */
    String staged() {
        return staged;
    }

    String lastError() {
        return lastError;
    }

    /** Checks Modrinth and installs or updates squaremap if needed. Background thread. */
    void check(boolean install, boolean update) {
        try {
            Plugin squaremap = Bukkit.getPluginManager().getPlugin("squaremap");
            File installed = squaremap == null ? findJar() : jarOf(squaremap);
            if (installed == null && !install || installed != null && !update) {
                return;
            }
            JsonObject release = latest();
            if (release == null) {
                return;
            }
            String version = release.get("version_number").getAsString();
            String current = squaremap != null ? squaremap.getPluginMeta().getVersion() : null;
            if (version.equals(staged) || current != null && Versions.compare(current, version) >= 0) {
                return;
            }
            JsonObject file = primaryFile(release.getAsJsonArray("files"));
            if (file == null) {
                throw new IOException("no file in the release");
            }
            Path target = installed == null
                    ? plugin.getDataFolder().getParentFile().toPath().resolve(file.get("filename").getAsString())
                    : Bukkit.getUpdateFolderFile().toPath().resolve(installed.getName());
            download(file.get("url").getAsString(), file.getAsJsonObject("hashes").get("sha512").getAsString(), target);
            staged = version;
            lastError = "";
            plugin.getLogger().info((installed == null ? "Installed squaremap " : "Downloaded squaremap update ")
                    + version + " from Modrinth - active after the next restart.");
            Bukkit.getScheduler().runTask(plugin, () -> plugin.discord().log("plugin-update-downloaded",
                    "plugin", "squaremap", "version", version, "installed", current == null ? "-" : current,
                    "admin", plugin.messages().server(() -> plugin.messages().plain("versions-auto"))));
        } catch (IOException | RuntimeException e) {
            lastError = String.valueOf(e.getMessage());
            plugin.getLogger().warning("squaremap could not be installed or updated: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The newest release (no betas) for this server's Minecraft version and Paper, or null if there is none. */
    private @Nullable JsonObject latest() throws IOException, InterruptedException {
        String query = "?loaders=" + encode("[\"paper\"]") + "&game_versions=" + encode("[\"" + Bukkit.getMinecraftVersion() + "\"]");
        HttpResponse<String> response = client.send(request(API + query).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Modrinth answered HTTP " + response.statusCode());
        }
        JsonObject best = null;
        for (JsonElement element : JsonParser.parseString(response.body()).getAsJsonArray()) {
            JsonObject version = element.getAsJsonObject();
            if (!"release".equals(version.get("version_type").getAsString())) {
                continue;
            }
            if (best == null || Versions.compare(version.get("version_number").getAsString(),
                    best.get("version_number").getAsString()) > 0) {
                best = version;
            }
        }
        if (best == null) {
            plugin.getLogger().info("No squaremap release for Minecraft " + Bukkit.getMinecraftVersion() + " on Modrinth yet.");
        }
        return best;
    }

    private static @Nullable JsonObject primaryFile(JsonArray files) {
        JsonObject first = null;
        for (JsonElement element : files) {
            JsonObject file = element.getAsJsonObject();
            if (file.has("primary") && file.get("primary").getAsBoolean()) {
                return file;
            }
            if (first == null) {
                first = file;
            }
        }
        return first;
    }

    /** Downloads, checks the SHA-512 and that it is squaremap, then moves it into place. */
    private void download(String url, String sha512, Path target) throws IOException, InterruptedException {
        if (!url.startsWith(CDN)) {
            throw new IOException("unexpected download address " + url);
        }
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".download");
        try {
            HttpResponse<InputStream> response = client.send(request(url).build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                response.body().close();
                throw new IOException("HTTP " + response.statusCode());
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            long total = 0;
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_DOWNLOAD) {
                        throw new IOException("file is larger than " + MAX_DOWNLOAD / 1024 / 1024 + " MB");
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            String hash = HexFormat.of().formatHex(digest.digest());
            if (!hash.equalsIgnoreCase(sha512)) {
                throw new IOException("checksum does not match");
            }
            if (!isSquaremap(temp.toFile())) {
                throw new IOException("the file is not squaremap");
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            temp = null;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        } finally {
            if (temp != null) {
                Files.deleteIfExists(temp);
            }
        }
    }

    /** squaremap is a Paper plugin: paper-plugin.yml (or plugin.yml) with "name: squaremap". */
    private static boolean isSquaremap(File jar) {
        try (ZipFile zip = new ZipFile(jar)) {
            for (String name : new String[]{"paper-plugin.yml", "plugin.yml"}) {
                var entry = zip.getEntry(name);
                if (entry != null) {
                    String yaml = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                    if (yaml.lines().anyMatch(line -> line.trim().equalsIgnoreCase("name: squaremap"))) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /** A squaremap JAR in plugins/ that Paper did not load (e.g. installed a moment ago), or null. */
    private @Nullable File findJar() {
        File[] jars = plugin.getDataFolder().getParentFile().listFiles((dir, name) ->
                name.toLowerCase(java.util.Locale.ROOT).startsWith("squaremap") && name.endsWith(".jar"));
        if (jars != null) {
            for (File jar : jars) {
                if (isSquaremap(jar)) {
                    return jar;
                }
            }
        }
        return null;
    }

    private static @Nullable File jarOf(Plugin p) {
        try {
            var source = p.getClass().getProtectionDomain().getCodeSource();
            File file = source == null ? null : new File(source.getLocation().toURI());
            return file != null && file.isFile() && file.getName().endsWith(".jar") ? file : null;
        } catch (Exception e) {
            return null;
        }
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("User-Agent", "therealmarci/MRadmin/" + plugin.getPluginMeta().getVersion()
                        + " (https://mrtechnology.marcelr.at)");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

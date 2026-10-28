package at.marcelr.mradmin.versions;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.util.Versions;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;

/**
 * The versions of all installed MRtechnology plugins and their updates.
 * <ul>
 *     <li>Found by the {@code build-info.properties} every MRtechnology plugin carries (GitHub repository, Minecraft
 *     version) - works with any version of the other plugins, no module API needed.</li>
 *     <li>Checked against the GitHub releases every {@code versions.check-interval-hours}.</li>
 *     <li>A download comes only from the official release of the plugin's own repository, for this Minecraft version,
 *     and must match the SHA-256 checksum GitHub keeps for the file. It is put into Paper's update folder
 *     ({@code plugins/update/}) under the name of the installed JAR - Paper swaps it in on the next start. Nothing is
 *     replaced while the server runs.</li>
 * </ul>
 * GitHub and downloads run on a background thread, the plugin list is read on the main thread.
 */
public final class VersionManager {

    private static final long MAX_DOWNLOAD = 64L * 1024 * 1024;
    private static final long MIN_CHECK_GAP = 60_000L;

    /** One MRtechnology plugin (state changes from the background thread - read it as a whole via {@link #list()}). */
    public static final class Entry {
        public final String name;
        public final String installed;
        public final String minecraft;
        public final String repository;
        public final String website;
        final File jar;
        public volatile ReleasePicker.Release latest;
        /** Newest client mod (only MRtoolbar has one). */
        public volatile ReleasePicker.Release mod;
        public volatile String error;
        /** Version waiting in plugins/update/ for the next start, or null. */
        public volatile String staged;
        public volatile boolean downloading;

        Entry(String name, String installed, String minecraft, String repository, String website, File jar) {
            this.name = name;
            this.installed = installed;
            this.minecraft = minecraft;
            this.repository = repository;
            this.website = website;
            this.jar = jar;
        }

        /** Whether a newer version than the installed (and the downloaded) one is published. */
        public boolean updateAvailable() {
            return latest != null && Versions.compare(latest.version(), installed) > 0
                    && (staged == null || Versions.compare(latest.version(), staged) > 0);
        }
    }

    private final MRAdmin plugin;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MRadmin-Versions");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private volatile long checkedAt;
    private volatile boolean checking;
    private BukkitTask task;

    public VersionManager(MRAdmin plugin) {
        this.plugin = plugin;
    }

    private Settings.VersionsSettings settings() {
        return plugin.settings().versions();
    }

    public boolean enabled() {
        return settings().enabled();
    }

    // --------------------------------------------------------------- lifecycle

    /** Looks at the installed plugins once all are enabled, then checks regularly. */
    public void start() {
        stop();
        if (!enabled()) {
            return;
        }
        long period = settings().checkHours() * 72_000L;
        // First run a few seconds after the start (all plugins are enabled by then).
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            discover();
            check(null);
        }, 200L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stop();
        worker.shutdownNow();
    }

    // ----------------------------------------------------------------- reading

    /** The plugins, sorted by name. */
    public List<Entry> list() {
        List<Entry> list = new ArrayList<>(entries.values());
        list.sort(Comparator.comparing(e -> e.name.toLowerCase(java.util.Locale.ROOT)));
        return list;
    }

    public Entry get(String name) {
        return entries.get(name);
    }

    public long checkedAt() {
        return checkedAt;
    }

    public boolean checking() {
        return checking;
    }

    // ------------------------------------------------------------------ discover

    /** Main thread: the installed MRtechnology plugins and what already waits in the update folder. */
    public void discover() {
        File updates = Bukkit.getUpdateFolderFile();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (!(p instanceof JavaPlugin javaPlugin)) {
                continue;
            }
            Properties info = new Properties();
            try (InputStream in = javaPlugin.getResource("build-info.properties")) {
                if (in == null) {
                    continue;
                }
                info.load(in);
            } catch (IOException e) {
                continue;
            }
            String repository = info.getProperty("githubRepository", "").trim();
            String minecraft = info.getProperty("mcVersion", "").trim();
            if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") || minecraft.isEmpty() || minecraft.contains("$")) {
                continue;
            }
            File jar = jarOf(p);
            if (jar == null) {
                continue;
            }
            String website = info.getProperty("website", "").trim();
            String slug = info.getProperty("websiteSlug", "").trim();
            Entry old = entries.get(p.getName());
            Entry entry = new Entry(p.getName(), Versions.plugin(p.getPluginMeta().getVersion()), minecraft, repository,
                    website.isEmpty() || website.contains("$") ? "https://github.com/" + repository
                            : website + "/download/" + (slug.isEmpty() ? p.getName().toLowerCase(java.util.Locale.ROOT) : slug) + "/",
                    jar);
            if (old != null) {
                entry.latest = old.latest;
                entry.mod = old.mod;
                entry.error = old.error;
                entry.downloading = old.downloading;
            }
            File waiting = new File(updates, jar.getName());
            entry.staged = waiting.isFile() ? versionIn(waiting) : null;
            entries.put(entry.name, entry);
        }
    }

    private static File jarOf(Plugin p) {
        try {
            var source = p.getClass().getProtectionDomain().getCodeSource();
            File file = source == null ? null : new File(source.getLocation().toURI());
            return file != null && file.isFile() && file.getName().endsWith(".jar") ? file : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The plugin version inside a JAR (plugin.yml), or "?" if it cannot be read. */
    private static String versionIn(File jar) {
        String[] meta = meta(jar);
        return meta == null ? "?" : Versions.plugin(meta[1]);
    }

    /** [name, version] from the plugin.yml of a JAR, or null. */
    private static String[] meta(File jar) {
        try (ZipFile zip = new ZipFile(jar)) {
            var yml = zip.getEntry("plugin.yml");
            if (yml == null) {
                return null;
            }
            String name = null;
            String version = null;
            for (String line : new String(zip.getInputStream(yml).readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.startsWith("name:")) {
                    name = unquote(line.substring(5));
                } else if (line.startsWith("version:")) {
                    version = unquote(line.substring(8));
                }
            }
            return name == null || version == null ? null : new String[]{name, version};
        } catch (IOException e) {
            return null;
        }
    }

    private static String unquote(String value) {
        String v = value.trim();
        return v.length() >= 2 && (v.startsWith("'") || v.startsWith("\"")) ? v.substring(1, v.length() - 1) : v;
    }

    // ------------------------------------------------------------------ check

    /**
     * Asks GitHub for the newest releases of all plugins (background thread); afterwards downloads what
     * {@code versions.auto-download} allows.
     *
     * @param by who asked (web), null for the timer
     * @return false if a check ran less than a minute ago (GitHub allows 60 requests per hour without login)
     */
    public boolean check(String by) {
        if (!enabled() || checking || (by != null && System.currentTimeMillis() - checkedAt < MIN_CHECK_GAP)) {
            return false;
        }
        checking = true;
        List<Entry> list = list();
        Settings.VersionsSettings s = settings();
        worker.execute(() -> {
            try {
                for (Entry e : list) {
                    checkOne(e, s.includePrereleases());
                }
                checkedAt = System.currentTimeMillis();
            } finally {
                checking = false;
            }
            for (Entry e : list) {
                if (e.updateAvailable() && ReleasePicker.autoAllowed(s.autoDownload(), e.installed, e.latest.version())) {
                    downloadNow(e, null);
                }
            }
        });
        return true;
    }

    private void checkOne(Entry e, boolean includePrereleases) {
        // Whoever runs a pre-release (0.x, -rc...) also gets newer pre-releases - like the update check.
        boolean prereleases = includePrereleases || Versions.isPrerelease(e.installed);
        try {
            String body = fetch("https://api.github.com/repos/" + e.repository + "/releases?per_page=50");
            JsonArray releases = JsonParser.parseString(body).getAsJsonArray();
            e.latest = ReleasePicker.pick(releases, e.name, e.minecraft, false, prereleases);
            e.mod = ReleasePicker.pick(releases, e.name, e.minecraft, true, true);
            e.error = e.latest == null ? "no-release" : null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            e.error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
    }

    private String fetch(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request(url).header("Accept", "application/vnd.github+json").build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 403 || response.statusCode() == 429) {
            throw new IOException("GitHub rate limit, try again later");
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "MRadmin/" + plugin.getPluginMeta().getVersion());
    }

    // --------------------------------------------------------------- download

    /**
     * Downloads the newest version of a plugin into the update folder (background thread).
     *
     * @param by who asked, null = automatic
     * @return null if the download started, otherwise an error key
     */
    public String download(String name, String by) {
        Entry e = entries.get(name);
        if (e == null) {
            return "not-found";
        }
        if (e.downloading) {
            return "versions-busy";
        }
        if (!e.updateAvailable()) {
            return "versions-no-update";
        }
        if (e.latest.sha256() == null) {
            return "versions-no-checksum";
        }
        e.downloading = true;
        worker.execute(() -> downloadNow(e, by));
        return null;
    }

    /** Background thread. Only files that match the checksum and contain this plugin land in the update folder. */
    private void downloadNow(Entry e, String by) {
        ReleasePicker.Release release = e.latest;
        e.downloading = true;
        Path temp = null;
        try {
            if (release == null || release.sha256() == null) {
                throw new IOException("no checksum published for this release");
            }
            String url = release.download();
            if (!url.startsWith("https://github.com/" + e.repository + "/releases/download/")) {
                throw new IOException("unexpected download address " + url);
            }
            Path folder = Bukkit.getUpdateFolderFile().toPath();
            Files.createDirectories(folder);
            temp = folder.resolve(e.jar.getName() + ".download");
            HttpResponse<InputStream> response = client.send(request(url).build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                response.body().close();
                throw new IOException("HTTP " + response.statusCode());
            }
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_DOWNLOAD) {
                        throw new IOException("file is larger than " + MAX_DOWNLOAD / 1024 / 1024 + " MB");
                    }
                    sha.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            String hash = HexFormat.of().formatHex(sha.digest());
            if (!hash.equalsIgnoreCase(release.sha256())) {
                throw new IOException("checksum does not match (expected " + release.sha256() + ", got " + hash + ")");
            }
            String[] meta = meta(temp.toFile());
            if (meta == null || !meta[0].equals(e.name)) {
                throw new IOException("the file does not contain " + e.name);
            }
            // Same name as the installed JAR - that is how Paper knows which plugin it replaces.
            Files.move(temp, folder.resolve(e.jar.getName()), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            temp = null;
            e.staged = release.version();
            e.error = null;
            String who = by == null ? "auto-download" : by;
            plugin.getLogger().info("Downloaded " + e.name + " " + release.version() + " (" + who
                    + ") - it is installed on the next start.");
            Bukkit.getScheduler().runTask(plugin, () -> plugin.discord().log("plugin-update-downloaded",
                    "plugin", e.name, "version", release.version(), "installed", e.installed,
                    "admin", by == null ? plugin.messages().server(() -> plugin.messages().plain("versions-auto")) : by));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            e.error = "download: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            plugin.getLogger().warning("Could not download " + e.name + " " + (release == null ? "" : release.version())
                    + ": " + e.error);
        } finally {
            e.downloading = false;
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // left for the next try
                }
            }
        }
    }
}

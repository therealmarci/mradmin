package at.marcelr.mradmin.versions;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.util.Versions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
    /** Rolling release of the dev channel (Minecraft "0" in the tag, so no update checker takes it for a version). */
    static final String DEV_TAG = "mc0-dev";
    private static final String CHANNELS = "versions-channels";

    /** Which releases a plugin gets. DEV = sealed builds of the dev branch, only with versions.dev-token. */
    public enum Channel {
        STABLE, BETA, DEV;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** "stable", "beta", "dev" -> channel, anything else null. */
        public static Channel of(String id) {
            for (Channel c : values()) {
                if (c.id().equals(id)) {
                    return c;
                }
            }
            return null;
        }
    }

    /**
     * The current build of the dev channel (from dev.json of the release {@value #DEV_TAG}).
     *
     * @param file      sealed file in the release, e.g. MRsleep-dev.jar.mrdev
     * @param sha256    checksum of the JAR inside (checked after opening)
     * @param base      version in gradle.properties on dev, e.g. STA-1.4.0-bu001
     */
    public record DevBuild(String commit, String shortCommit, long date, int run, String file, String sha256, long size,
                           String base, String minecraft) {
    }

    /** One MRtechnology plugin (state changes from the background thread - read it as a whole via {@link #list()}). */
    public static final class Entry {
        public final String name;
        /** Running version, null if the plugin is not installed (only known from the {@link Catalog}). */
        public final String installed;
        public final String minecraft;
        public final String repository;
        public final String website;
        /** Commit and branch the running JAR was built from (only builds of GitHub Actions know them, else ""). */
        public final String installedCommit;
        public final String installedBranch;
        /** The running JAR, or for a plugin that is not running the JAR lying in plugins/ (null if there is none). */
        final File jar;
        public volatile ReleasePicker.Release latest;
        /** Newest client mod (only MRtoolbar has one). */
        public volatile ReleasePicker.Release mod;
        public volatile String error;
        /** Version waiting in plugins/update/ for the next start, or null. */
        public volatile String staged;
        /** Commit of the JAR waiting in plugins/update/ (dev builds), or "". */
        public volatile String stagedCommit = "";
        /** Not installed: version of the JAR put into plugins/, loaded on the next start (null if there is none). */
        public volatile String pending;
        /** Not installed, but its JAR was already there when the server started - Paper did not load it (see the console). */
        public volatile boolean notLoaded;
        public volatile boolean downloading;
        /** Channel this plugin follows (its own choice or versions.channel). */
        public volatile Channel channel = Channel.STABLE;
        /** True if the team chose the channel for this plugin, false if it follows versions.channel. */
        public volatile boolean ownChannel;
        /** Current dev build (looked up whenever a release token is set), or null. */
        public volatile DevBuild dev;
        /** Whether a stable / a beta release exists for this Minecraft version (no stable = stable greyed out). */
        public volatile boolean hasStable;
        public volatile boolean hasBeta;
        /** A blocked step back (DowngradeGuard) and for which offer (release version / dev commit) it holds, or null. */
        public volatile DowngradeGuard.Block blocked;
        volatile String blockedFor;

        Entry(String name, String installed, String minecraft, String repository, String website, File jar,
              String installedCommit, String installedBranch) {
            this.name = name;
            this.installed = installed;
            this.minecraft = minecraft;
            this.repository = repository;
            this.website = website;
            this.jar = jar;
            this.installedCommit = installedCommit;
            this.installedBranch = installedBranch;
        }

        public boolean isInstalled() {
            return installed != null;
        }

        /** The running JAR is a build of the dev branch. */
        public boolean runsDevBuild() {
            return "dev".equals(installedBranch) && !installedCommit.isEmpty();
        }

        /**
         * Whether there is something newer than the installed (and the downloaded) version on the plugin's channel. On the
         * dev channel: a dev build of another commit. Otherwise: a newer release (never an older one - switching back
         * from beta or dev to stable waits for the next stable).
         */
        public boolean updateAvailable() {
            return newerOffered() && !blockedNow();
        }

        /** The offer is blocked as a step back (DowngradeGuard) - no button, no automatic download. */
        public boolean blockedNow() {
            String key = offerKey();
            return blocked != null && key != null && key.equals(blockedFor);
        }

        /** Release version or dev commit on offer - what a block refers to. */
        String offerKey() {
            return channel == Channel.DEV ? (dev == null ? null : dev.commit()) : latest == null ? null : latest.version();
        }

        /** Something newer than installed and downloaded on the channel (before the downgrade rules). */
        boolean newerOffered() {
            if (installed == null) {
                return false;
            }
            if (channel == Channel.DEV) {
                return dev != null && !dev.commit().equals(installedCommit) && !dev.commit().equals(stagedCommit);
            }
            return latest != null && Versions.compare(latest.version(), installed) > 0
                    && (staged == null || Versions.compare(latest.version(), staged) > 0);
        }

        /** Not installed, nothing in plugins/ yet and something to install on its channel. */
        public boolean installable() {
            if (installed != null || pending != null || notLoaded || blockedNow()) {
                return false;
            }
            return channel == Channel.DEV ? dev != null : latest != null && latest.sha256() != null;
        }

        /**
         * The channel as shown: a plugin without any stable release (0.x) gets betas anyway, so it counts as beta.
         */
        public Channel shownChannel() {
            return channel == Channel.STABLE && !hasStable && hasBeta ? Channel.BETA : channel;
        }

        /** What would be downloaded: version of the release, or "dev abc1234" on the dev channel. */
        public String offered() {
            return channel == Channel.DEV ? (dev == null ? null : "dev " + dev.shortCommit())
                    : latest == null ? null : latest.version();
        }

        /** A downloaded update or a new plugin that the next start brings in. */
        public boolean waitsForRestart() {
            return staged != null || (pending != null && !notLoaded);
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
    /** Channel chosen per plugin on the page Plugins (storage "versions-channels"); missing = versions.channel. */
    private final Map<String, Channel> chosen = new ConcurrentHashMap<>();
    private volatile long checkedAt;
    private volatile boolean checking;
    /** Whether versions.dev-token opens the token check of the dev channel; null = not known yet (or no token). */
    private volatile Boolean devTokenValid;
    private volatile String devTokenChecked = "";
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

    // --------------------------------------------------------------- channels

    /** The default channel (versions.channel). */
    public Channel defaultChannel() {
        return settings().includePrereleases() ? Channel.BETA : Channel.STABLE;
    }

    /** Channel of a plugin: its own choice, else the default. Dev without (valid) release token counts as the default. */
    private Channel channelOf(String name) {
        Channel own = chosen.get(name);
        if (own == Channel.DEV && !devAllowed()) {
            return defaultChannel();
        }
        return own == null ? defaultChannel() : own;
    }

    /** A release token is set and not known to be wrong (unknown right after the start counts as fine). */
    private boolean devAllowed() {
        return settings().hasDevToken() && !Boolean.FALSE.equals(validity());
    }

    /** Validity of the current token (forgotten when the token changes). */
    private Boolean validity() {
        return settings().devToken().equals(devTokenChecked) ? devTokenValid : null;
    }

    /** "none" (no token), "valid", "invalid" or "unknown" (not checked yet). */
    public String devTokenState() {
        if (!settings().hasDevToken()) {
            return "none";
        }
        Boolean valid = validity();
        return valid == null ? "unknown" : valid ? "valid" : "invalid";
    }

    /** Whether the dev channel is offered: a release token is set and it opened the token check. */
    public boolean devAvailable() {
        return settings().hasDevToken() && Boolean.TRUE.equals(validity());
    }

    /**
     * Sets the channel of a plugin and looks it up again.
     *
     * @param channel null = follow versions.channel again
     * @return null if done, otherwise an error key
     */
    public String setChannel(String name, Channel channel, String by) {
        Entry e = entries.get(name);
        if (e == null) {
            return "not-found";
        }
        if (channel == Channel.DEV && !settings().hasDevToken()) {
            return "versions-dev-token-missing";
        }
        if (channel == Channel.DEV && Boolean.FALSE.equals(validity())) {
            return "versions-dev-token-invalid";
        }
        // Stable cannot be chosen before there is a stable release, dev not without a dev build. Beta always can - it
        // also gets the stables, and one may want to stay on beta while the newest release is a stable.
        if (channel == Channel.STABLE && !e.hasStable && e.hasBeta
                || channel == Channel.DEV && e.dev == null && validity() != null) {
            return "versions-channel-empty";
        }
        if (channel == null) {
            chosen.remove(name);
        } else {
            chosen.put(name, channel);
        }
        saveChannels();
        e.channel = channelOf(name);
        e.ownChannel = chosen.containsKey(name);
        plugin.getLogger().info("Channel of " + name + ": " + e.channel.id() + (e.ownChannel ? "" : " (default)") + " (" + by + ").");
        worker.execute(() -> checkOne(e));
        return null;
    }

    private void loadChannels() {
        chosen.clear();
        org.bukkit.configuration.file.YamlConfiguration yaml = plugin.storage().loadYaml(CHANNELS, plugin.getLogger());
        for (String name : yaml.getKeys(false)) {
            Channel c = Channel.of(String.valueOf(yaml.getString(name)).toLowerCase(java.util.Locale.ROOT));
            if (c != null) {
                chosen.put(name, c);
            }
        }
        // Chosen in the installer (mrserver setup --channel dev): taken over once.
        if (!StarterLink.SEEDED_CHANNELS.isEmpty()) {
            StarterLink.SEEDED_CHANNELS.forEach((name, id) -> {
                Channel c = Channel.of(id.toLowerCase(java.util.Locale.ROOT));
                if (c != null) {
                    chosen.putIfAbsent(name, c);
                }
            });
            StarterLink.SEEDED_CHANNELS.clear();
            saveChannels();
        }
    }

    private void saveChannels() {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        chosen.forEach((name, c) -> yaml.set(name, c.id()));
        plugin.storage().saveYaml(CHANNELS, yaml);
    }

    // --------------------------------------------------------------- lifecycle

    /** Looks at the installed plugins once all are enabled, then checks regularly. */
    public void start() {
        stop();
        if (!enabled()) {
            return;
        }
        loadChannels();
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
                    jar, plain(info.getProperty("gitCommit")), plain(info.getProperty("gitBranch")));
            carry(old, entry);
            File waiting = new File(updates, jar.getName());
            entry.staged = waiting.isFile() ? versionIn(waiting) : null;
            entry.stagedCommit = waiting.isFile() ? plain(buildInfo(waiting).getProperty("gitCommit")) : "";
            entries.put(entry.name, entry);
        }
        discoverMissing();
    }

    /**
     * The catalogue plugins that do not run: offered for installation, or - if their JAR already lies in plugins/ - shown
     * as waiting for the next start (new file) or as not loaded (the file was there when the server started).
     */
    private void discoverMissing() {
        Properties own = new Properties();
        try (InputStream in = plugin.getResource("build-info.properties")) {
            if (in != null) {
                own.load(in);
            }
        } catch (IOException ignored) {
            // without it no Minecraft version - nothing to offer
        }
        String minecraft = own.getProperty("mcVersion", "").trim();
        String website = own.getProperty("website", "").trim();
        if (minecraft.isEmpty() || minecraft.contains("$")) {
            return;
        }
        Map<String, File> lying = new java.util.HashMap<>();
        File[] files = plugin.getDataFolder().getParentFile().listFiles((dir, n) -> n.endsWith(".jar"));
        for (File f : files == null ? new File[0] : files) {
            String[] meta = meta(f);
            if (meta != null) {
                lying.putIfAbsent(meta[0], f);
            }
        }
        long started = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
        for (Catalog.Known known : Catalog.PLUGINS) {
            if (Bukkit.getPluginManager().getPlugin(known.name()) != null) {
                continue;
            }
            File jar = lying.get(known.name());
            Entry old = entries.get(known.name());
            Entry entry = new Entry(known.name(), null, minecraft, known.repository(),
                    website.isEmpty() || website.contains("$") ? "https://github.com/" + known.repository()
                            : website + "/download/" + known.slug() + "/", jar, "", "");
            carry(old, entry);
            entry.pending = jar == null ? null : versionIn(jar);
            entry.notLoaded = jar != null && jar.lastModified() < started;
            entries.put(entry.name, entry);
        }
    }

    /** Keeps what the last check found and sets the channel. */
    private void carry(Entry old, Entry entry) {
        if (old != null) {
            entry.latest = old.latest;
            entry.mod = old.mod;
            entry.dev = old.dev;
            entry.blocked = old.blocked;
            entry.blockedFor = old.blockedFor;
            entry.error = old.error;
            entry.downloading = old.downloading;
        }
        entry.channel = channelOf(entry.name);
        entry.ownChannel = chosen.containsKey(entry.name);
    }

    /** A value of build-info.properties without the unfilled placeholder of local builds. */
    private static String plain(String value) {
        return value == null || value.contains("$") ? "" : value.trim();
    }

    /** build-info.properties inside a JAR (empty if there is none). */
    private static Properties buildInfo(File jar) {
        Properties info = new Properties();
        try (ZipFile zip = new ZipFile(jar)) {
            var entry = zip.getEntry("build-info.properties");
            if (entry != null) {
                try (InputStream in = zip.getInputStream(entry)) {
                    info.load(in);
                }
            }
        } catch (IOException ignored) {
            // no build info - not one of ours or broken
        }
        return info;
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
                // The timer reads the website's file with all releases (no GitHub API limit); a click on "check now"
                // asks GitHub directly, so a release of a few minutes ago shows up at once.
                feed = by == null ? loadFeed() : null;
                for (Entry e : list) {
                    checkOne(e);
                }
                feed = null;
                checkedAt = System.currentTimeMillis();
            } finally {
                checking = false;
            }
            for (Entry e : list) {
                // A new release on offer (not downloaded by itself below): a team notice, once per version.
                if (e.isInstalled() && e.channel != Channel.DEV && e.latest != null && e.updateAvailable()
                        && !ReleasePicker.autoAllowed(s.autoDownload(), e.installed, e.latest.version())) {
                    plugin.push("update-available", "available:" + e.name + ":" + e.latest.version(), "update-available",
                            "/admin/server/plugins", null, "plugin", e.name, "version", e.latest.version(),
                            "installed", e.installed);
                }
                // Dev builds come by themselves only with auto-download: all.
                boolean auto = e.channel == Channel.DEV ? s.autoDownload() == ReleasePicker.Auto.ALL
                        : e.latest != null && ReleasePicker.autoAllowed(s.autoDownload(), e.installed, e.latest.version());
                if (e.updateAvailable() && auto) {
                    downloadNow(e, null);
                }
            }
        });
        return true;
    }

    private void checkOne(Entry e) {
        lookUp(e);
        checkCode(e);
    }

    private void lookUp(Entry e) {
        e.channel = channelOf(e.name);
        // Betas on the beta and dev channel, and always for 0.x - like the update check (Versions.wantsBeta).
        boolean prereleases = Versions.wantsBeta(e.installed, e.channel != Channel.STABLE);
        try {
            JsonElement fromFeed = feed == null ? null : feed.get(e.repository.toLowerCase(java.util.Locale.ROOT));
            String body = fromFeed != null && fromFeed.isJsonArray() ? fromFeed.toString()
                    : fetch("https://api.github.com/repos/" + e.repository + "/releases?per_page=50");
            JsonArray releases = JsonParser.parseString(body).getAsJsonArray();
            e.latest = ReleasePicker.pick(releases, e.name, e.minecraft, false, prereleases);
            if (e.latest == null && !e.isInstalled()) {
                // Not installed and only pre-releases so far (e.g. MRpvp 0.x): offer the newest pre-release.
                e.latest = ReleasePicker.pick(releases, e.name, e.minecraft, false, true);
            }
            e.mod = ReleasePicker.pick(releases, e.name, e.minecraft, true, true);
            ReleasePicker.Release newest = ReleasePicker.pick(releases, e.name, e.minecraft, false, true);
            e.hasStable = ReleasePicker.pick(releases, e.name, e.minecraft, false, false) != null;
            e.hasBeta = newest != null && newest.prerelease();
            e.error = e.latest == null ? "no-release" : null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception ex) {
            e.error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
        // The dev channel on its own: dev.json is a plain download, not limited like the GitHub API above - it must
        // work even while the API says "rate limit".
        if (!settings().hasDevToken()) {
            e.dev = null;
            return;
        }
        try {
            e.dev = devBuild(e);
            e.channel = channelOf(e.name);
            if (e.channel == Channel.DEV) {
                e.error = e.dev == null ? "dev-none" : null;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            if (e.channel == Channel.DEV) {
                e.error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            }
        }
    }

    /** Commit comparisons already asked ("base...head" -> status), so GitHub is asked once per pair. */
    private final Map<String, String> compared = new ConcurrentHashMap<>();

    /**
     * Downgrade rule 2 (code): if the running build and the offer are both known commits, the offer must be a descendant
     * (GitHub compare "ahead"). Asked only when there is a newer offer - one API request per new pair.
     */
    private void checkCode(Entry e) {
        String key = e.offerKey();
        if (key == null || !e.newerOffered() || e.installedCommit.isEmpty()) {
            return;
        }
        String head = e.channel == Channel.DEV ? e.dev.commit() : e.latest.tag();
        String pair = e.installedCommit + "..." + head;
        String status = compared.get(pair);
        if (status == null) {
            try {
                String body = fetch("https://api.github.com/repos/" + e.repository + "/compare/" + pair);
                status = JsonParser.parseString(body).getAsJsonObject().get("status").getAsString();
                compared.put(pair, status);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                return; // unknown - rule 1 still guards the download
            }
        }
        DowngradeGuard.Block block = DowngradeGuard.code(status, e.installedCommit, head);
        if (block != null) {
            if (!key.equals(e.blockedFor)) {
                plugin.getLogger().warning(e.name + " " + e.offered() + " is not offered: it would be a step back from the running"
                        + " build (" + status + "). Going back is only possible by hand.");
            }
            e.blocked = block;
            e.blockedFor = key;
        }
    }

    /** Downgrade rule 1 (migrations) on the downloaded JAR; true = blocked (the file must not be installed). */
    private boolean migrationsBlock(Entry e, File jar) throws IOException {
        DowngradeGuard.Block block = DowngradeGuard.migrations(jar, new File(plugin.getDataFolder().getParentFile(), e.name));
        if (block == null) {
            return false;
        }
        e.blocked = block;
        e.blockedFor = e.offerKey();
        plugin.getLogger().warning("Not installing " + e.name + " " + e.offered() + ": its " + block.reason() + "-version "
                + block.offered() + " is older than " + block.have() + " on this server - the settings were already migrated"
                + " by a newer version. Going back is only possible by hand.");
        return true;
    }

    /**
     * The current dev build of a plugin from dev.json of the release {@value #DEV_TAG}, or null if there is none for
     * this Minecraft version. The files are public, the JAR inside is sealed with the release token.
     */
    private DevBuild devBuild(Entry e) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request(devUrl(e, "dev.json")).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("dev.json: HTTP " + response.statusCode());
        }
        String token = settings().devToken();
        Boolean valid = DevJson.tokenValid(response.body(), token);
        if (valid != null) {
            if (!valid.equals(validity())) {
                plugin.getLogger().info(valid ? "Dev channel: the release token (versions.dev-token) is valid."
                        : "Dev channel: the release token (versions.dev-token) is wrong - the channel dev stays hidden.");
            }
            devTokenChecked = token;
            devTokenValid = valid;
        }
        return DevJson.pick(response.body(), e.name, e.minecraft);
    }

    private static String devUrl(Entry e, String file) {
        return "https://github.com/" + e.repository + "/releases/download/" + DEV_TAG + "/" + file;
    }

    /**
     * One file with the releases of all MRtechnology plugins, built by the website from GitHub - GitHub allows only 60
     * API requests per hour and IP without login, several servers behind one address used that up quickly.
     */
    private static final String RELEASE_FEED = "https://mrtechnology.marcelr.at/api/releases.json";
    /** The repositories of the feed while a check runs (null = ask GitHub). */
    private volatile com.google.gson.JsonObject feed;

    /** The website's release file, null if it cannot be read (then GitHub is asked). */
    private com.google.gson.JsonObject loadFeed() {
        try {
            HttpResponse<String> r = client.send(request(RELEASE_FEED).header("Accept", "application/json").build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 == 2) {
                return JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("repos");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            plugin.getLogger().fine("Release file of the website not readable: " + ex.getMessage());
        }
        return null;
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
        if (e.blockedNow()) {
            return "versions-downgrade";
        }
        if (!e.updateAvailable()) {
            return "versions-no-update";
        }
        if (e.channel != Channel.DEV && e.latest.sha256() == null) {
            return "versions-no-checksum";
        }
        e.downloading = true;
        worker.execute(() -> downloadNow(e, by));
        return null;
    }

    /**
     * Installs a plugin that is not on the server yet: its newest release goes straight into plugins/ (background
     * thread). Paper loads it on the next start - there is no loading while the server runs.
     *
     * @param by who asked
     * @return null if the download started, otherwise an error key
     */
    public String install(String name, String by) {
        Entry e = entries.get(name);
        if (e == null) {
            return "not-found";
        }
        if (e.downloading) {
            return "versions-busy";
        }
        if (e.isInstalled() || e.pending != null || e.notLoaded) {
            return "versions-already-installed";
        }
        if (e.channel == Channel.DEV) {
            if (e.dev == null) {
                return "versions-no-release";
            }
        } else if (e.latest == null) {
            return "versions-no-release";
        } else if (e.latest.sha256() == null) {
            return "versions-no-checksum";
        }
        e.downloading = true;
        worker.execute(() -> downloadNow(e, by));
        return null;
    }

    /**
     * Background thread. Only files that match the checksum and contain this plugin are kept: an update goes into the
     * update folder under the name of the installed JAR, a new plugin into plugins/ under the name of the release file.
     */
    private void downloadNow(Entry e, String by) {
        if (e.channel == Channel.DEV) {
            downloadDev(e, by);
            return;
        }
        ReleasePicker.Release release = e.latest;
        boolean fresh = !e.isInstalled();
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
            if (fresh && !release.asset().matches("[A-Za-z0-9_.+-]+\\.jar")) {
                throw new IOException("unexpected file name " + release.asset());
            }
            Path folder = fresh ? plugin.getDataFolder().getParentFile().toPath() : Bukkit.getUpdateFolderFile().toPath();
            String fileName = fresh ? release.asset() : e.jar.getName();
            Files.createDirectories(folder);
            temp = folder.resolve(fileName + ".download");
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
            if (migrationsBlock(e, temp.toFile())) {
                return;
            }
            // An update keeps the name of the installed JAR - that is how Paper knows which plugin it replaces.
            Files.move(temp, folder.resolve(fileName), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            temp = null;
            if (fresh) {
                e.pending = release.version();
            } else {
                e.staged = release.version();
            }
            e.error = null;
            String who = by == null ? "auto-download" : by;
            plugin.getLogger().info((fresh ? "Installed " : "Downloaded ") + e.name + " " + release.version() + " (" + who
                    + ") - it is " + (fresh ? "loaded" : "installed") + " on the next start.");
            if (!fresh) {
                plugin.push("update-ready", "plugin:" + e.name + ":" + release.version(), "update-ready", "/admin/server/plugins",
                        null, "plugin", e.name, "version", release.version());
                Bukkit.getScheduler().runTask(plugin, () -> plugin.discord().log("plugin-update-downloaded",
                        "plugin", e.name, "version", release.version(), "installed", e.installed,
                        "admin", by == null ? plugin.messages().server(() -> plugin.messages().plain("versions-auto")) : by));
            }
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

    /**
     * Background thread: the current dev build, opened with versions.dev-token. Kept only if the JAR inside matches the
     * checksum of dev.json and contains this plugin - then it goes where a release would go (update folder under the
     * installed name, or plugins/ for a new plugin).
     */
    private void downloadDev(Entry e, String by) {
        DevBuild dev = e.dev;
        boolean fresh = !e.isInstalled();
        e.downloading = true;
        Path temp = null;
        try {
            if (dev == null) {
                throw new IOException("no dev build");
            }
            String token = settings().devToken();
            if (token.isBlank()) {
                throw new IOException("no versions.dev-token");
            }
            HttpResponse<InputStream> response = client.send(request(devUrl(e, dev.file())).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                response.body().close();
                throw new IOException("HTTP " + response.statusCode());
            }
            byte[] sealed;
            try (InputStream in = response.body()) {
                sealed = in.readNBytes((int) MAX_DOWNLOAD + 1);
            }
            if (sealed.length > MAX_DOWNLOAD) {
                throw new IOException("file is larger than " + MAX_DOWNLOAD / 1024 / 1024 + " MB");
            }
            byte[] jar;
            try {
                jar = at.marcelr.mradmin.starter.DevSeal.open(sealed, token);
            } catch (at.marcelr.mradmin.starter.DevSeal.BadTokenException ex) {
                throw new IOException("dev-token: " + ex.getMessage());
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar));
            if (!hash.equalsIgnoreCase(dev.sha256())) {
                throw new IOException("checksum does not match (expected " + dev.sha256() + ", got " + hash + ")");
            }
            Path folder = fresh ? plugin.getDataFolder().getParentFile().toPath() : Bukkit.getUpdateFolderFile().toPath();
            String fileName = fresh ? e.name + "-mc" + e.minecraft + "-dev.jar" : e.jar.getName();
            Files.createDirectories(folder);
            temp = folder.resolve(fileName + ".download");
            Files.write(temp, jar);
            String[] meta = meta(temp.toFile());
            if (meta == null || !meta[0].equals(e.name)) {
                throw new IOException("the file does not contain " + e.name);
            }
            if (migrationsBlock(e, temp.toFile())) {
                return;
            }
            Files.move(temp, folder.resolve(fileName), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            temp = null;
            String label = "dev " + dev.shortCommit();
            if (fresh) {
                e.pending = label;
            } else {
                e.staged = label;
                e.stagedCommit = dev.commit();
            }
            e.error = null;
            String who = by == null ? "auto-download" : by;
            plugin.getLogger().info((fresh ? "Installed " : "Downloaded ") + e.name + " " + label + " (based on " + dev.base()
                    + ", " + who + ") - it is " + (fresh ? "loaded" : "installed") + " on the next start.");
            if (!fresh) {
                plugin.push("update-ready", "plugin:" + e.name + ":" + label, "update-ready", "/admin/server/plugins",
                        null, "plugin", e.name, "version", label);
                Bukkit.getScheduler().runTask(plugin, () -> plugin.discord().log("plugin-update-downloaded",
                        "plugin", e.name, "version", label, "installed", e.installed,
                        "admin", by == null ? plugin.messages().server(() -> plugin.messages().plain("versions-auto")) : by));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            e.error = "download: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            plugin.getLogger().warning("Could not download the dev build of " + e.name + ": " + e.error);
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

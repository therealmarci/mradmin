package at.marcelr.mradmin.versions;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Texts;
import at.marcelr.mradmin.starter.Paper;
import at.marcelr.mradmin.starter.Setup;
import at.marcelr.mradmin.starter.StarterFiles;
import at.marcelr.mradmin.util.DurationUtil;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * MRadmin's side of the starter (mrserver): only active if the server was started by it (environment variable
 * {@link StarterFiles#ENV}). Writes the settings for the next start, a heartbeat while the server ticks, the wish
 * restart/stop before shutting down, answers {@code mrserver stop/restart/cancel} and reports what the starter saw
 * (crashes, hangs, Paper updates). Planned restarts ({@code server.restart-times}) run here too - with or without the
 * starter, as long as something brings the server back.
 */
public final class StarterLink {

    private final MRAdmin plugin;
    private final StarterFiles files;
    private ServerSettings settings;
    private final Set<String> planned = new HashSet<>();
    private boolean warnedNoRestart;
    private volatile long lastTick;
    private volatile String wish;
    private BukkitTask tickTask;
    private BukkitTask ioTask;
    private BukkitTask scheduleTask;
    private volatile Paper.Build paperLatest;
    private volatile long paperChecked;
    private volatile boolean paperFailed;
    private int paperAnnounced = -1;
    private BukkitTask paperTask;

    public StarterLink(MRAdmin plugin) {
        this.plugin = plugin;
        String env = System.getenv(StarterFiles.ENV);
        this.files = env == null || env.isBlank() ? null : new StarterFiles(Path.of(env));
    }

    /** Whether the server runs under the starter - then restarts always work. */
    public boolean active() {
        return files != null;
    }

    public ServerSettings settings() {
        return settings;
    }

    /** Newest stable Paper build of this Minecraft version, if known and newer than the running one. */
    public Paper.Build paperUpdate() {
        Paper.Build b = paperLatest;
        return b != null && b.id() > Paper.buildOf(Bukkit.getVersion()) ? b : null;
    }

    /** On enable and after /mradmin reload. */
    public void start() {
        settings = ServerSettings.load(plugin.getConfig(), plugin.getLogger());
        warnedNoRestart = false;
        stopTasks();
        scheduleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkSchedule, 200L, 200L);
        // Paper: first check after 10 seconds, then every 15 minutes (shown on the server overview).
        paperTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkPaper, 200L, 15 * 60 * 20L);
        if (!active()) {
            return;
        }
        lastTick = System.currentTimeMillis();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> lastTick = System.currentTimeMillis(), 20L, 20L);
        ioTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::io, 20L, 20L);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            writeLaunch();
            report();
            refreshStarter();
        });
    }

    /**
     * Answers of the installer (mrserver setup --memory-min/--memory-max/--web/--web-url, file seed.properties):
     * written once into config.yml on the first start - before storage and web interface start - and the file is
     * deleted. Only these keys, so nothing else can be set this way.
     */
    /** Plugin channels from the installation (seed.properties) - the VersionManager takes them over once. */
    static final java.util.Map<String, String> SEEDED_CHANNELS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void applySeed(MRAdmin plugin) {
        String env = System.getenv(StarterFiles.ENV);
        if (env == null || env.isBlank()) {
            return;
        }
        StarterFiles starter = new StarterFiles(Path.of(env));
        Properties seed = StarterFiles.read(starter.seed());
        if (seed.isEmpty()) {
            return;
        }
        List<String> taken = new java.util.ArrayList<>();
        for (String key : at.marcelr.mradmin.starter.Setup.SEED_KEYS) {
            String value = seed.getProperty(key);
            if (value == null) {
                continue;
            }
            plugin.getConfig().set(key, key.equals("web.enabled") ? (Object) Boolean.parseBoolean(value) : value);
            taken.add(key.equals("web.public-url") || key.equals("versions.dev-token") ? key : key + "=" + value);
        }
        for (String key : seed.stringPropertyNames()) {
            if (key.startsWith(at.marcelr.mradmin.starter.Setup.SEED_PLUGIN_CHANNEL)) {
                String name = key.substring(at.marcelr.mradmin.starter.Setup.SEED_PLUGIN_CHANNEL.length());
                SEEDED_CHANNELS.put(name, seed.getProperty(key).trim());
                taken.add("channel of " + name + "=" + seed.getProperty(key).trim());
            }
        }
        plugin.saveConfig();
        try {
            Files.deleteIfExists(starter.seed());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not delete " + starter.seed() + ": " + e.getMessage());
        }
        plugin.getLogger().info("Settings from the installation taken over into config.yml: " + String.join(", ", taken) + ".");
    }

    public void stop() {
        stopTasks();
        if (active() && !Files.isRegularFile(files.action())) {
            // A plain /stop (or Paper shutting down by itself): what server.plain-stop says.
            write(wish != null ? wish : settings == null ? "stop" : settings.plainStop());
        }
    }

    private void stopTasks() {
        for (BukkitTask t : new BukkitTask[] {tickTask, ioTask, scheduleTask, paperTask}) {
            if (t != null) {
                t.cancel();
            }
        }
        tickTask = ioTask = scheduleTask = paperTask = null;
    }

    /** RestartManager, right before the server goes down. */
    void prepare(boolean restart) {
        wish = restart ? StarterFiles.ACTION_RESTART : StarterFiles.ACTION_STOP;
        if (active()) {
            write(wish);
        }
    }

    private void write(String action) {
        try {
            StarterFiles.writeText(files.action(), action);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not tell the starter what to do after shutdown: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ every second (async)

    private void io() {
        try {
            StarterFiles.writeText(files.heartbeat(), String.valueOf(lastTick));
        } catch (IOException ignored) {
            // Next second.
        }
        String request = StarterFiles.readText(files.request());
        if (request != null) {
            try {
                Files.deleteIfExists(files.request());
            } catch (IOException ignored) {
                // Answered anyway.
            }
            Bukkit.getScheduler().runTask(plugin, () -> answer(request));
        }
    }

    /** mrserver stop|restart|cancel [time] - answered in request.answer (English, console tool). */
    private void answer(String request) {
        // "kind time timestamp [reason ...]" - the reason may contain spaces.
        String[] parts = request.split(" ", 4);
        String reason = parts.length > 3 ? parts[3] : null;
        String kind = parts[0];
        String time = parts.length > 1 ? parts[1] : "-";
        String text;
        RestartManager restarts = plugin.restarts();
        if (kind.equals("cancel")) {
            text = restarts.cancel("console (mrserver)") ? "OK - countdown cancelled." : "No countdown running.";
        } else {
            long delay = time.equals("-") ? settings.countdown()
                    : time.equals("now") || time.equals("jetzt") ? 0
                    : time.matches("[0-9]+") ? Long.parseLong(time) * 1000L : DurationUtil.parse(time);
            if (delay < 0 || delay > 24 * 3_600_000L) {
                text = "Invalid time: " + time;
            } else if (restarts.running()) {
                text = "A countdown is already running - mrserver cancel first.";
            } else if (restarts.start(delay, "console (mrserver)", kind.equals("restart"), reason)) {
                text = "OK - " + (kind.equals("restart") ? "restart" : "stop")
                        + (delay == 0 ? " now." : " in " + Texts.duration(delay) + " (announced in game).");
            } else {
                text = "Could not start the countdown.";
            }
        }
        try {
            StarterFiles.writeText(files.request().resolveSibling("request.answer"), text);
        } catch (IOException ignored) {
            // mrserver falls back to the console.
        }
    }

    // ------------------------------------------------------------------ planned restarts

    private void checkSchedule() {
        if (settings.restartTimes().isEmpty() || plugin.restarts().running()) {
            return;
        }
        if (!plugin.restarts().enabled()) {
            if (!warnedNoRestart) {
                warnedNoRestart = true;
                plugin.getLogger().warning("server.restart-times is set, but nothing brings the server back - planned "
                        + "restarts are skipped. Use the starter (/mradmin starter setup) or versions.restart-method.");
            }
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(plugin.settings().zone());
        for (LocalTime time : settings.restartTimes()) {
            for (LocalDate day : List.of(now.toLocalDate(), now.toLocalDate().plusDays(1))) {
                ZonedDateTime at = day.atTime(time).atZone(now.getZone());
                long until = at.toInstant().toEpochMilli() - now.toInstant().toEpochMilli();
                String key = at.toString();
                if (until > 0 && until <= settings.countdown() + 15_000L && planned.add(key)) {
                    plugin.getLogger().info("Planned restart at " + time + ".");
                    String why = plugin.messages().server(() -> plugin.messages().plain("restart-reason-schedule"))
                            .replace("{time}", time.toString());
                    plugin.restarts().start(until, "schedule", true, why);
                    return;
                }
            }
        }
        if (planned.size() > 50) {
            planned.clear();
        }
    }

    /** The next planned restart, or null. */
    public ZonedDateTime nextPlanned() {
        if (settings == null || settings.restartTimes().isEmpty()) {
            return null;
        }
        ZonedDateTime now = ZonedDateTime.now(plugin.settings().zone());
        ZonedDateTime best = null;
        for (LocalTime time : settings.restartTimes()) {
            ZonedDateTime at = now.toLocalDate().atTime(time).atZone(now.getZone());
            if (!at.isAfter(now)) {
                at = at.plusDays(1);
            }
            if (best == null || at.isBefore(best)) {
                best = at;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ files for the starter

    private void writeLaunch() {
        Properties p = new Properties();
        p.setProperty("memory-min", settings.memoryMin());
        p.setProperty("memory-max", settings.memoryMax());
        p.setProperty("java-flags", settings.javaFlags());
        p.setProperty("paper-update", settings.paperUpdate());
        p.setProperty("minecraft", Bukkit.getMinecraftVersion());
        p.setProperty("paper-build", String.valueOf(Paper.buildOf(Bukkit.getVersion())));
        p.setProperty("crash-max", String.valueOf(settings.crashMax()));
        p.setProperty("crash-window-minutes", String.valueOf(settings.crashWindowMinutes()));
        p.setProperty("hang-seconds", String.valueOf(settings.hangSeconds()));
        p.setProperty("stop-timeout-seconds", String.valueOf(settings.stopTimeoutSeconds()));
        try {
            StarterFiles.write(files.launch(), p, "Written by MRadmin from config.yml (section server) - do not edit.");
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write the starter settings: " + e.getMessage());
        }
    }

    /** What the starter saw since the last report: console, protocol and Discord admin log. */
    private void report() {
        Path marker = files.dir().resolve("events.reported");
        long reported;
        try {
            reported = Long.parseLong(java.util.Objects.requireNonNullElse(StarterFiles.readText(marker), "0"));
        } catch (NumberFormatException e) {
            reported = 0;
        }
        List<StarterFiles.Event> events = files.readEvents();
        long last = reported;
        for (StarterFiles.Event e : events) {
            if (e.time() <= reported) {
                continue;
            }
            last = Math.max(last, e.time());
            String[] d = e.detail().split(" ");
            String when = Texts.at(plugin, java.time.Instant.ofEpochMilli(e.time()));
            switch (e.kind()) {
                case "crash" -> {
                    plugin.getLogger().warning("The server crashed at " + when + " (" + e.detail() + ") - the starter started it again.");
                    plugin.discord().log("server-crash", "time", when, "reason", e.detail());
                }
                case "hang" -> {
                    plugin.getLogger().warning("The server hung at " + when + " (" + e.detail() + " s without a tick) - the starter ended and started it.");
                    plugin.discord().log("server-hang", "time", when, "seconds", e.detail());
                }
                case "gave-up" -> {
                    plugin.getLogger().warning("The starter gave up at " + when + " after " + d[0] + " crashes.");
                    plugin.discord().log("server-gave-up", "time", when, "count", d[0], "minutes", d.length > 1 ? d[1] : "?");
                }
                case "paper-update" -> {
                    plugin.getLogger().info("Paper was updated from build " + d[0] + " to " + (d.length > 1 ? d[1] : "?") + ".");
                    plugin.discord().log("paper-updated", "old", d[0], "new", d.length > 1 ? d[1] : "?");
                }
                case "paper-rollback" -> {
                    plugin.getLogger().warning("Paper build " + d[0] + " did not start - the starter went back to build "
                            + (d.length > 1 ? d[1] : "?") + ". It is skipped until a newer build comes.");
                    plugin.discord().log("paper-rollback", "bad", d[0], "old", d.length > 1 ? d[1] : "?");
                }
                default -> {
                    // stop-timeout and future kinds: console only.
                    plugin.getLogger().info("Starter: " + e.kind() + " " + e.detail() + " (" + when + ")");
                }
            }
        }
        try {
            StarterFiles.writeText(marker, String.valueOf(last));
            if (events.size() > 200) {
                Files.write(files.events(), events.subList(events.size() - 200, events.size()).stream()
                        .map(StarterFiles.Event::line).toList());
            }
        } catch (IOException ignored) {
            // Reported again next time - better than never.
        }
    }

    private void refreshStarter() {
        try {
            if (Setup.refresh(files, plugin.jarFile().toPath())) {
                plugin.getLogger().info("New starter version prepared - it is used from the next restart on.");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not update the starter files: " + e.getMessage());
        }
    }

    /** Every 15 minutes, with or without the starter: is the running Paper the newest stable build? */
    private void checkPaper() {
        try {
            Paper.Build build = new Paper(at.marcelr.mradmin.starter.Main.userAgent()).latestStable(Bukkit.getMinecraftVersion());
            paperLatest = build;
            paperFailed = false;
            Paper.Build update = paperUpdate();
            // Once per new build in the console, not every 15 minutes.
            if (update != null && update.id() != paperAnnounced) {
                paperAnnounced = update.id();
                boolean auto = active() && settings.paperUpdate().equals("auto");
                plugin.getLogger().info("Paper build " + update.id() + " is available (running "
                        + Paper.buildOf(Bukkit.getVersion()) + ")" + (auto
                        ? " - the starter installs it on the next restart." : " - install it by hand or use the starter."));
            }
        } catch (IOException | RuntimeException e) {
            paperFailed = true;
            plugin.getLogger().fine("Paper update check failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            paperChecked = System.currentTimeMillis();
        }
    }

    /** Newest stable Paper build of this Minecraft version, -1 if not known (yet). */
    public int paperLatestBuild() {
        Paper.Build b = paperLatest;
        return b == null ? -1 : b.id();
    }

    /** When Paper was last checked (epoch ms), 0 = not yet. */
    public long paperChecked() {
        return paperChecked;
    }

    /** Whether the last check failed (no connection to PaperMC). */
    public boolean paperFailed() {
        return paperFailed;
    }

    /** /mradmin starter setup: the files for the starter in the server folder (no downloads). */
    public Setup.Result setup(String mode) throws IOException {
        StarterFiles target = new StarterFiles(Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize());
        return Setup.prepare(target, plugin.jarFile().toPath(), mode == null ? null : mode.toLowerCase(Locale.ROOT));
    }
}

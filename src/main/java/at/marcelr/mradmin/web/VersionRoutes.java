package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.storage.Storage;
import at.marcelr.mradmin.versions.ReleasePicker;
import at.marcelr.mradmin.versions.VersionManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The page Plugins: all MRtechnology plugins (installed or not), their updates, installing and the restart button.
 * <pre>
 *  GET  /api/versions                    plugins, last check, restart countdown, choices    mradmin.versions.view
 *  POST /api/versions/check              ask GitHub now (at most once a minute)            mradmin.versions.install
 *  POST /api/versions/{name}/download    newest version into plugins/update/               mradmin.versions.install
 *  POST /api/versions/{name}/install     plugin that is not installed into plugins/        mradmin.versions.install
 *  POST /api/versions/{name}/channel {channel: default|stable|beta|dev}                     mradmin.versions.install
 *  POST /api/restart       {delay, kind, reason} restart (kind=stop: stop) after delay ms mradmin.server.restart
 *  POST /api/restart/cancel                                                                 mradmin.server.restart
 *  POST /api/restart/schedule {pause}   pause planned restarts for 24 h / switch them on   mradmin.server.restart
 *  POST /api/restart/confirm {code | passkey}  sign in again for an emergency restart (2 min) mradmin.server.restart
 * </pre>
 * Downloads and restarts go into the web log.
 */
public final class VersionRoutes {

    private final WebServer server;
    private final MRAdmin plugin;
    /** How long an emergency confirmation is valid, and how wrong codes lock it. */
    private static final long CONFIRM_MILLIS = 120_000L;
    private static final int MAX_FAILURES = 5;
    private static final long FAILURE_LOCK_MILLIS = 15 * 60_000L;
    /** session id -> emergency restart allowed until (epoch ms), used up by the restart */
    private final java.util.Map<String, Long> emergencyOk = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, int[]> failures = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> lockedUntil = new java.util.concurrent.ConcurrentHashMap<>();

    private VersionRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        VersionRoutes r = new VersionRoutes(server);
        server.route("GET", "/api/versions", r::state);
        server.route("POST", "/api/versions/check", r::check);
        server.route("POST", "/api/versions/{name}/download", r::download);
        server.route("POST", "/api/versions/{name}/install", r::install);
        server.route("POST", "/api/versions/{name}/channel", r::channel);
        server.route("POST", "/api/restart", r::restart);
        server.route("POST", "/api/restart/cancel", r::cancel);
        server.route("POST", "/api/restart/schedule", r::schedule);
        server.route("POST", "/api/restart/confirm", r::confirmEmergency);
        server.route("POST", "/api/waitroom/restart", r::waitroomRestart);
    }

    /** Viewing: mradmin.versions.view, or install (its parent - LuckPerms does not always pass children on offline). */
    static boolean mayView(WebServer server, java.util.UUID account) {
        return server.permissions().has(account, Perms.VERSIONS_VIEW) || server.permissions().has(account, Perms.VERSIONS_INSTALL);
    }

    private void require(Http http, boolean allowed) throws Http.WebException {
        if (!plugin.versions().enabled()) {
            throw new Http.WebException(404, "feature-disabled");
        }
        if (!allowed) {
            throw new Http.WebException(403, "no-permission");
        }
    }

    private boolean may(Http http, String permission) {
        return server.permissions().has(http.account().id(), permission);
    }

    private void state(Http http) throws Exception {
        require(http, mayView(server, http.account().id()));
        VersionManager versions = plugin.versions();
        List<Map<String, Object>> plugins = new ArrayList<>();
        // Plugins whose config.yml can be edited here (only for mradmin.config).
        java.util.Set<String> configurable = ConfigRoutes.may(server, http.account().id())
                ? server.sync(() -> plugin.configEditor().targets().keySet()) : java.util.Set.of();
        for (VersionManager.Entry e : versions.list()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", e.name);
            if (e.remote) {
                // On the waiting room server: managed through its reports (network.RemotePlugins).
                var remote = plugin.waitingRoom().remote();
                var report = remote.report(e.name);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("online", report != null && report.online());
                r.put("server", report == null ? "" : report.server());
                r.put("seen", report == null ? 0 : report.at());
                r.put("ordered", remote.openOrder(e.name, "update"));
                r.put("configPending", remote.openOrder(e.name, "config") != null);
                r.put("error", remote.error(e.name));
                p.put("remote", r);
            }
            p.put("installed", e.installed);
            p.put("minecraft", e.minecraft);
            p.put("website", e.website);
            p.put("latest", release(e.latest));
            p.put("mod", release(e.mod));
            p.put("update", e.updateAvailable());
            p.put("staged", e.staged);
            p.put("pending", e.pending);
            p.put("notLoaded", e.notLoaded);
            p.put("installable", e.installable());
            p.put("config", configurable.contains(e.name));
            p.put("downloading", e.downloading);
            p.put("error", e.error);
            p.put("checksum", e.channel == VersionManager.Channel.DEV || (e.latest != null && e.latest.sha256() != null));
            p.put("channel", e.shownChannel().id());
            // Which channels can be chosen: stable only once there is a stable release (or nothing at all yet), beta
            // always (it also gets stables), dev only with a valid release token and a dev build.
            p.put("channels", Map.of("stable", e.hasStable || !e.hasBeta, "beta", true, "dev", versions.devAvailable() && e.dev != null));
            p.put("ownChannel", e.ownChannel);
            p.put("offered", e.offered());
            if (e.installed != null) {
                p.put("installedInfo", installedInfo(e));
            }
            // A step back that is not offered (DowngradeGuard): reason config / lang / code, the two values.
            if (e.blockedNow()) {
                p.put("blocked", Map.of("reason", e.blocked.reason(), "have", e.blocked.have(), "offered", e.blocked.offered()));
            }
            p.put("devBuild", e.runsDevBuild() ? e.installedCommit.substring(0, Math.min(7, e.installedCommit.length())) : null);
            if (e.dev != null) {
                Map<String, Object> dev = new LinkedHashMap<>();
                dev.put("commit", e.dev.shortCommit());
                dev.put("date", e.dev.date());
                dev.put("run", e.dev.run());
                dev.put("base", e.dev.base());
                dev.put("size", e.dev.size());
                p.put("dev", dev);
            }
            if (e.name.equals(plugin.getName()) && plugin.map() != null) {
                // squaremap belongs to MRadmin (it renders the map, MRadmin installs and updates it).
                Map<String, Object> squaremap = new LinkedHashMap<>();
                squaremap.put("version", plugin.map().version());
                squaremap.put("staged", plugin.map().staged());
                p.put("squaremap", squaremap);
            }
            plugins.add(p);
        }
        Map<String, Object> restart = new LinkedHashMap<>();
        restart.put("endsAt", server.sync(() -> plugin.restarts().endsAt()));
        restart.put("by", plugin.restarts().by());
        restart.put("reason", server.sync(() -> plugin.restarts().reason()));
        restart.put("method", plugin.settings().versions().restartMethod());
        restart.put("delays", plugin.settings().versions().restartDelays());
        restart.put("emergencyDelays", plugin.settings().versions().emergencyDelays());
        // The running countdown is the one of a planned restart (cancelling it skips only this one).
        restart.put("planned", "schedule".equals(plugin.restarts().by()) && plugin.restarts().endsAt() > 0);
        // The server tab: countdown kind, starter, Paper build, next planned restart, since when the server runs.
        restart.put("kind", plugin.restarts().restarting() ? "restart" : "stop");
        restart.put("enabled", plugin.restarts().enabled());
        var starter = plugin.starter();
        Map<String, Object> starterInfo = new LinkedHashMap<>();
        starterInfo.put("active", starter.active());
        starterInfo.put("mode", starter.active() ? System.getenv("MRADMIN_STARTER_MODE") : null);
        // Set up (mrserver.properties there) but the server was started another way.
        starterInfo.put("ready", !starter.active() && new java.io.File(org.bukkit.Bukkit.getWorldContainer(), "mrserver.properties").isFile());
        restart.put("starter", starterInfo);
        Map<String, Object> paper = new LinkedHashMap<>();
        paper.put("build", at.marcelr.mradmin.starter.Paper.buildOf(org.bukkit.Bukkit.getVersion()));
        paper.put("minecraft", org.bukkit.Bukkit.getMinecraftVersion());
        var update = starter.paperUpdate();
        // latest = a newer build (null if current or unknown); newest = newest stable build at all, -1 = not checked yet.
        paper.put("latest", update == null ? null : update.id());
        paper.put("newest", starter.paperLatestBuild());
        // Downloaded ahead (with the starter, paper-update auto): put in place on the next restart, -1 = none.
        paper.put("staged", update != null && starter.paperStaged() == update.id() ? starter.paperStaged() : -1);
        paper.put("checkedAt", starter.paperChecked());
        paper.put("failed", starter.paperFailed());
        paper.put("update", starter.settings() == null ? "auto" : starter.settings().paperUpdate());
        restart.put("paper", paper);
        var next = starter.nextPlanned();
        restart.put("nextPlanned", next == null ? 0 : next.toInstant().toEpochMilli());
        Map<String, Object> schedule = new LinkedHashMap<>();
        schedule.put("times", starter.settings() == null ? 0 : starter.settings().restartTimes().size());
        schedule.put("pausedUntil", server.sync(starter::pausedUntil));
        schedule.put("pausedBy", server.sync(starter::pausedBy));
        restart.put("schedule", schedule);
        restart.put("startedAt", java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plugins", plugins);
        result.put("checkedAt", versions.checkedAt());
        result.put("checking", versions.checking());
        result.put("auto", plugin.settings().versions().autoDownload().name().toLowerCase(java.util.Locale.ROOT));
        result.put("channel", versions.defaultChannel().id());
        // Dev is only shown with a valid token; "invalid" = set but it does not open the token check of the dev channel.
        result.put("devToken", versions.devAvailable());
        result.put("devTokenState", versions.devTokenState());
        result.put("restart", restart);
        // The waiting room server (MRwaitroom reports itself through waiting-room.link).
        var remoteRoom = plugin.waitingRoom().remote().report("MRwaitroom");
        if (plugin.waitingRoom().active() && remoteRoom != null) {
            Map<String, Object> room = new LinkedHashMap<>(remoteRoom.host());
            room.put("online", remoteRoom.online() && plugin.waitingRoom().roomUp());
            room.put("seen", remoteRoom.at());
            room.put("version", remoteRoom.version());
            room.put("ordered", plugin.waitingRoom().remote().openOrder("MRwaitroom", "restart") != null);
            room.put("error", plugin.waitingRoom().remote().error("MRwaitroom"));
            result.put("waitroom", room);
        }
        result.put("may", Map.of("install", may(http, Perms.VERSIONS_INSTALL),
                "restart", plugin.restarts().enabled() && may(http, Perms.SERVER_RESTART),
                "stop", may(http, Perms.SERVER_RESTART)));
        http.json(200, result);
    }

    /** POST /api/waitroom/restart - the waiting room restarts as soon as it may (it checks the rules itself). */
    private void waitroomRestart(Http http) throws Exception {
        require(http, may(http, Perms.SERVER_RESTART));
        String problem = plugin.waitingRoom().remote().orderRestart("MRwaitroom", http.account().name());
        if (problem != null) {
            throw new Http.WebException(409, problem);
        }
        audit(http, "waitroom-restart", "");
        http.json(200, Map.of("ok", true));
    }

    /**
     * What runs, in parts: channel (stable / beta / dev), number (1.4.0), build (null for old versions without one) and
     * the commit if the JAR was built by GitHub Actions.
     */
    private static Map<String, Object> installedInfo(VersionManager.Entry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("channel", e.runsDevBuild() ? "dev"
                : at.marcelr.mradmin.util.Versions.channel(e.installed).name().toLowerCase(java.util.Locale.ROOT));
        m.put("number", at.marcelr.mradmin.util.Versions.number(e.installed));
        m.put("build", at.marcelr.mradmin.util.Versions.isScheme(e.installed) ? at.marcelr.mradmin.util.Versions.build(e.installed) : null);
        m.put("commit", e.installedCommit.isEmpty() ? null : e.installedCommit.substring(0, Math.min(7, e.installedCommit.length())));
        return m;
    }

    private static Map<String, Object> release(ReleasePicker.Release r) {
        if (r == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", r.version());
        m.put("prerelease", r.prerelease());
        m.put("page", r.page());
        m.put("size", r.size());
        m.put("published", r.published());
        m.put("notes", r.notes().length() > 20_000 ? r.notes().substring(0, 20_000) + " …" : r.notes());
        return m;
    }

    private void check(Http http) throws Exception {
        require(http, may(http, Perms.VERSIONS_INSTALL));
        server.sync(() -> {
            plugin.versions().discover();
            return null;
        });
        if (!plugin.versions().check(http.account().name() + " (web)")) {
            throw new Http.WebException(429, "versions-wait");
        }
        http.json(200, Map.of());
    }

    private void download(Http http) throws Exception {
        require(http, may(http, Perms.VERSIONS_INSTALL));
        String name = http.param("name");
        VersionManager.Entry e = plugin.versions().get(name);
        String error = plugin.versions().download(name, http.account().name() + " (web)");
        if (error != null) {
            throw new Http.WebException(error.equals("not-found") ? 404 : 400, error);
        }
        audit(http, "plugin-download", name + " " + e.offered());
        http.json(200, Map.of());
    }

    private void install(Http http) throws Exception {
        require(http, may(http, Perms.VERSIONS_INSTALL));
        String name = http.param("name");
        VersionManager.Entry e = plugin.versions().get(name);
        String error = plugin.versions().install(name, http.account().name() + " (web)");
        if (error != null) {
            throw new Http.WebException(error.equals("not-found") ? 404 : 400, error);
        }
        audit(http, "plugin-install", name + " " + e.offered());
        http.json(200, Map.of());
    }

    private void channel(Http http) throws Exception {
        require(http, may(http, Perms.VERSIONS_INSTALL));
        String name = http.param("name");
        String raw = String.valueOf(http.string("channel")).trim().toLowerCase(java.util.Locale.ROOT);
        VersionManager.Channel channel = VersionManager.Channel.of(raw);
        if (channel == null && !raw.equals("default")) {
            throw new Http.WebException(400, "invalid-value");
        }
        String error = plugin.versions().setChannel(name, channel, http.account().name() + " (web)");
        if (error != null) {
            throw new Http.WebException(error.equals("not-found") ? 404 : 400, error);
        }
        audit(http, "plugin-channel", name + " " + raw);
        http.json(200, Map.of());
    }

    private void restart(Http http) throws Exception {
        require(http, may(http, Perms.SERVER_RESTART));
        // A stop works without anything that brings the server back - a restart does not.
        boolean stop = "stop".equals(http.string("kind"));
        if (!stop && !plugin.restarts().enabled()) {
            throw new Http.WebException(400, "restart-disabled");
        }
        long delay;
        try {
            delay = Long.parseLong(String.valueOf(http.string("delay")).trim());
        } catch (NumberFormatException ex) {
            throw new Http.WebException(400, "invalid-duration");
        }
        // Only the configured choices - nobody restarts in three days by accident. The short emergency choices only
        // with a reason (players should normally get at least 15 minutes warning).
        String reason = http.string("reason");
        boolean emergency = !plugin.settings().versions().restartDelays().contains(delay);
        if (emergency && !plugin.settings().versions().emergencyDelays().contains(delay)) {
            throw new Http.WebException(400, "invalid-duration");
        }
        if (emergency && (reason == null || reason.isBlank())) {
            throw new Http.WebException(400, "restart-reason-required");
        }
        // An emergency needs a fresh sign-in (POST /api/restart/confirm) - used up here.
        if (emergency) {
            Long until = emergencyOk.remove(http.session().id());
            if (until == null || until < System.currentTimeMillis()) {
                throw new Http.WebException(403, "restart-confirm-required");
            }
        }
        String by = http.account().name() + " (web)";
        if (!server.sync(() -> plugin.restarts().start(delay, by, !stop, reason))) {
            throw new Http.WebException(409, "restart-running");
        }
        audit(http, stop ? "server-stop" : "server-restart", delay / 1000 + " s" + (emergency ? " (emergency)" : ""));
        http.json(200, Map.of());
    }

    private void cancel(Http http) throws Exception {
        require(http, may(http, Perms.SERVER_RESTART));
        String by = http.account().name() + " (web)";
        if (!server.sync(() -> plugin.restarts().cancel(by))) {
            throw new Http.WebException(409, "restart-none");
        }
        audit(http, "server-restart-cancelled", "");
        http.json(200, Map.of());
    }

    /** Emergency restart: confirm with a fresh 2FA code or a passkey like the web console; 5 wrong codes lock 15 min. */
    private void confirmEmergency(Http http) throws Exception {
        require(http, may(http, Perms.SERVER_RESTART));
        java.util.UUID account = http.account().id();
        long now = System.currentTimeMillis();
        if (lockedUntil.getOrDefault(account, 0L) > now) {
            throw new Http.WebException(429, "console-code-locked");
        }
        if (!server.confirmed(http)) {
            int[] count = failures.computeIfAbsent(account, k -> new int[1]);
            if (++count[0] >= MAX_FAILURES) {
                count[0] = 0;
                lockedUntil.put(account, now + FAILURE_LOCK_MILLIS);
                audit(http, "server-emergency-locked", "5 wrong codes");
            }
            throw new Http.WebException(400, "code-wrong");
        }
        failures.remove(account);
        emergencyOk.put(http.session().id(), now + CONFIRM_MILLIS);
        audit(http, "server-emergency-confirmed", "");
        http.json(200, Map.of("until", now + CONFIRM_MILLIS));
    }

    private void schedule(Http http) throws Exception {
        require(http, may(http, Perms.SERVER_RESTART));
        boolean pause = Boolean.parseBoolean(String.valueOf(http.string("pause")));
        String by = http.account().name() + " (web)";
        server.sync(() -> {
            plugin.starter().pauseSchedule(pause, by);
            return null;
        });
        audit(http, pause ? "server-schedule-paused" : "server-schedule-resumed", "");
        http.json(200, Map.of());
    }

    private void audit(Http http, String action, String detail) {
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), http.account().id(), http.account().name(),
                http.storedIp(), action, detail));
    }
}

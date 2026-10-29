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
 * The page Versions: installed MRtechnology plugins, their updates and the restart button.
 * <pre>
 *  GET  /api/versions                    plugins, last check, restart countdown, choices    mradmin.versions.view
 *  POST /api/versions/check              ask GitHub now (at most once a minute)            mradmin.versions.install
 *  POST /api/versions/{name}/download    newest version into plugins/update/               mradmin.versions.install
 *  POST /api/restart       {delay}       restart after delay ms (0 = now)                  mradmin.server.restart
 *  POST /api/restart/cancel                                                                 mradmin.server.restart
 * </pre>
 * Downloads and restarts go into the web log.
 */
public final class VersionRoutes {

    private final WebServer server;
    private final MRAdmin plugin;

    private VersionRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        VersionRoutes r = new VersionRoutes(server);
        server.route("GET", "/api/versions", r::state);
        server.route("POST", "/api/versions/check", r::check);
        server.route("POST", "/api/versions/{name}/download", r::download);
        server.route("POST", "/api/restart", r::restart);
        server.route("POST", "/api/restart/cancel", r::cancel);
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
        for (VersionManager.Entry e : versions.list()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", e.name);
            p.put("installed", e.installed);
            p.put("minecraft", e.minecraft);
            p.put("website", e.website);
            p.put("latest", release(e.latest));
            p.put("mod", release(e.mod));
            p.put("update", e.updateAvailable());
            p.put("staged", e.staged);
            p.put("downloading", e.downloading);
            p.put("error", e.error);
            p.put("checksum", e.latest != null && e.latest.sha256() != null);
            plugins.add(p);
        }
        Map<String, Object> restart = new LinkedHashMap<>();
        restart.put("endsAt", server.sync(() -> plugin.restarts().endsAt()));
        restart.put("by", plugin.restarts().by());
        restart.put("method", plugin.settings().versions().restartMethod());
        restart.put("delays", plugin.settings().versions().restartDelays());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plugins", plugins);
        result.put("checkedAt", versions.checkedAt());
        result.put("checking", versions.checking());
        result.put("auto", plugin.settings().versions().autoDownload().name().toLowerCase(java.util.Locale.ROOT));
        result.put("restart", restart);
        result.put("may", Map.of("install", may(http, Perms.VERSIONS_INSTALL),
                "restart", plugin.restarts().enabled() && may(http, Perms.SERVER_RESTART)));
        http.json(200, result);
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
        audit(http, "plugin-download", name + " " + e.latest.version());
        http.json(200, Map.of());
    }

    private void restart(Http http) throws Exception {
        require(http, may(http, Perms.SERVER_RESTART));
        if (!plugin.restarts().enabled()) {
            throw new Http.WebException(400, "restart-disabled");
        }
        long delay;
        try {
            delay = Long.parseLong(String.valueOf(http.string("delay")).trim());
        } catch (NumberFormatException ex) {
            throw new Http.WebException(400, "invalid-duration");
        }
        // Only the configured choices - nobody restarts in three days by accident.
        if (!plugin.settings().versions().restartDelays().contains(delay)) {
            throw new Http.WebException(400, "invalid-duration");
        }
        String by = http.account().name() + " (web)";
        if (!server.sync(() -> plugin.restarts().start(delay, by))) {
            throw new Http.WebException(409, "restart-running");
        }
        audit(http, "server-restart", delay / 1000 + " s");
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

    private void audit(Http http, String action, String detail) {
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), http.account().id(), http.account().name(),
                http.storedIp(), action, detail));
    }
}

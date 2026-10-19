package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.storage.Storage;

import java.util.Map;
import java.util.Set;

/**
 * MRchunks in the web interface - through MRchunks' own hook (version 4), so both plugins stay independent. The
 * permissions are MRchunks' own: mrchunks.admin.gui to look, mrchunks.admin.manage to switch, unlock or delete.
 * <pre>
 *  GET  /api/mrchunks/overview
 *  GET  /api/mrchunks/loaders
 *  GET  /api/mrchunks/loaders/{id}
 *  POST /api/mrchunks/loaders/{id}/{action}    toggle | unlock | delete
 * </pre>
 * Without MRchunks (or with integrations.mrchunks: false) everything answers 404 "plugin-missing".
 */
public final class ChunkRoutes {

    public static final String PERM_VIEW = "mrchunks.admin.gui";
    public static final String PERM_MANAGE = "mrchunks.admin.manage";
    public static final String PERM_LIMIT = "mrchunks.admin.limit";
    public static final String PERM_BAN = "mrchunks.admin.ban";
    private static final Set<String> ACTIONS = Set.of("toggle", "unlock", "delete");

    private final WebServer server;
    private final MRAdmin plugin;

    private ChunkRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        ChunkRoutes r = new ChunkRoutes(server);
        server.route("GET", "/api/mrchunks/overview", http -> r.get(http, "overview", Map.of()));
        server.route("GET", "/api/mrchunks/loaders", http -> r.get(http, "loaders", Map.of()));
        server.route("GET", "/api/mrchunks/loaders/{id}", http -> r.get(http, "loader", Map.of("id", http.param("id"))));
        server.route("POST", "/api/mrchunks/loaders/{id}/{action}", r::action);
    }

    /** The MRchunks button if MRchunks runs, is connected and offers web data; otherwise null. */
    public static Settings.HubButton hook(MRAdmin plugin) {
        return plugin.settings().adminMode().buttons().stream()
                .filter(b -> b.id().equals("mrchunks") && plugin.hooks().available(b) && plugin.hooks().hasWeb(b))
                .findFirst().orElse(null);
    }

    private Settings.HubButton require(Http http, String permission) throws Http.WebException {
        Settings.HubButton hook = hook(plugin);
        if (hook == null) {
            throw new Http.WebException(404, "plugin-missing");
        }
        if (!server.permissions().has(http.account().id(), permission)) {
            throw new Http.WebException(403, "no-permission");
        }
        return hook;
    }

    private void get(Http http, String request, Map<String, Object> args) throws Exception {
        Settings.HubButton hook = require(http, PERM_VIEW);
        Object answer = server.sync(() -> plugin.hooks().web(hook, request, args));
        if (answer == null) {
            throw new Http.WebException(404, "not-found");
        }
        http.json(200, answer);
    }

    private void action(Http http) throws Exception {
        String action = http.param("action");
        if (!ACTIONS.contains(action)) {
            throw new Http.WebException(404, "not-found");
        }
        Settings.HubButton hook = require(http, PERM_MANAGE);
        String id = http.param("id");
        Object error = server.sync(() -> plugin.hooks().web(hook, "action",
                Map.of("id", id, "action", action, "admin", http.account().name())));
        if (error != null) {
            throw new Http.WebException(error.equals("not-found") ? 404 : 400, String.valueOf(error));
        }
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), http.account().id(), http.account().name(),
                http.storedIp(), "chunkloader-" + action, "#" + id));
    }
}

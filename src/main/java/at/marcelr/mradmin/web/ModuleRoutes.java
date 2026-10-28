package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.api.AdminModule;
import at.marcelr.mradmin.api.WebModule;
import at.marcelr.mradmin.api.WebUser;
import at.marcelr.mradmin.storage.Storage;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The web part of every module (MRchunks, MRsleep, MRpvp ... - see {@link at.marcelr.mradmin.module.Modules}), one set
 * of routes for all. The module names the permission each request and action needs ({@link WebModule}); MRadmin
 * checks it (plus the module's view permission) before it asks the module.
 * <pre>
 *  GET  /api/modules/{id}/data/{request}?arg=value      data of the module (e.g. mrchunks: overview, loaders, loader?id=3)
 *  POST /api/modules/{id}/actions/{action}   {args}     an action (e.g. mrchunks: toggle {id: 3})
 *  GET  /api/players/{uuid}/modules/{id}                the module's part of a player's page
 *  POST /api/players/{uuid}/modules/{id}/{action}       an action on the player (e.g. mrchunks: limit {kind, value})
 * </pre>
 * Without the module (plugin missing, or integrations.&lt;id&gt;: false) everything answers 404 "plugin-missing".
 * Every action goes into the web protocol as "&lt;id&gt;-&lt;action&gt;".
 */
public final class ModuleRoutes {

    private static final int MAX_TEXT = 200;

    private final WebServer server;
    private final MRAdmin plugin;

    private ModuleRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        ModuleRoutes r = new ModuleRoutes(server);
        server.route("GET", "/api/modules/{id}/data/{request}", r::data);
        server.route("POST", "/api/modules/{id}/actions/{action}", r::action);
        server.route("GET", "/api/players/{uuid}/modules/{id}", r::player);
        server.route("POST", "/api/players/{uuid}/modules/{id}/{action}", r::playerAction);
    }

    // ---------------------------------------------------------------- access

    /**
     * Whether the member has the module's permission {@code key} ("view" = the module's view permission; any other
     * key additionally needs viewing). False if the module is not connected.
     */
    public static boolean may(WebServer server, UUID account, String moduleId, String key) {
        MRAdmin plugin = server.plugin();
        AdminModule module = plugin.modules().module(moduleId);
        if (module == null || !server.permissions().has(account, module.viewPermission())) {
            return false;
        }
        if (key.equals("view")) {
            return true;
        }
        WebModule web = plugin.modules().web(moduleId);
        String permission = web == null ? null : web.permissions().get(key);
        return permission != null && server.permissions().has(account, permission);
    }

    /** The module's web part, after checking the permission key; 404 / 403 otherwise. */
    private WebModule require(Http http, String moduleId, String key) throws Http.WebException {
        WebModule web = plugin.modules().web(moduleId);
        if (web == null) {
            throw new Http.WebException(404, "plugin-missing");
        }
        if (key == null) {
            throw new Http.WebException(400, "unknown-action");
        }
        if (!may(server, http.account().id(), moduleId, key)) {
            throw new Http.WebException(403, "no-permission");
        }
        return web;
    }

    private static WebUser user(Http http) {
        return new WebUser(http.account().name(), http.account().id());
    }

    private static UUID uuid(Http http) throws Http.WebException {
        try {
            return UUID.fromString(http.param("uuid"));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new Http.WebException(400, "invalid-player");
        }
    }

    // ---------------------------------------------------------------- routes

    private void data(Http http) throws Exception {
        String id = http.param("id");
        String request = http.param("request");
        WebModule web = plugin.modules().web(id);
        WebModule checked = require(http, id, web == null ? "view" : web.requests().getOrDefault(request, "view"));
        Map<String, String> args = http.query();
        Object answer = server.sync(() -> plugin.modules().call(plugin.modules().module(id),
                () -> checked.data(request, args), null));
        if (answer == null) {
            throw new Http.WebException(404, "not-found");
        }
        http.json(200, answer);
    }

    private void action(Http http) throws Exception {
        String id = http.param("id");
        String action = http.param("action");
        WebModule web = plugin.modules().web(id);
        WebModule checked = require(http, id, web == null ? "view" : web.actions().get(action));
        // Viewing is needed too - an action never works on a page the member cannot see.
        Map<String, Object> args = limit(http.bodyTree());
        Object error = server.sync(() -> plugin.modules().attempt(plugin.modules().module(id),
                () -> checked.action(action, args, user(http))));
        fail(error);
        audit(http, id + "-" + action, describe(args));
    }

    private void player(Http http) throws Exception {
        String id = http.param("id");
        UUID player = uuid(http);
        requirePlayerView(http);
        WebModule web = require(http, id, "view");
        Object answer = server.sync(() -> plugin.modules().call(plugin.modules().module(id), () -> web.player(player), null));
        if (answer == null) {
            throw new Http.WebException(404, "not-found");
        }
        http.json(200, answer);
    }

    private void playerAction(Http http) throws Exception {
        String id = http.param("id");
        String action = http.param("action");
        UUID player = uuid(http);
        requirePlayerView(http);
        WebModule web = plugin.modules().web(id);
        WebModule checked = require(http, id, web == null ? "view" : web.playerActions().get(action));
        Map<String, Object> args = limit(http.bodyTree());
        Object error = server.sync(() -> plugin.modules().attempt(plugin.modules().module(id),
                () -> checked.playerAction(player, action, args, user(http))));
        fail(error);
        String detail = describe(args);
        audit(http, id + "-" + action, name(player) + (detail.isEmpty() ? "" : " " + detail));
    }

    // --------------------------------------------------------------- helpers

    /** The player page itself needs mradmin.player.view. */
    private void requirePlayerView(Http http) throws Http.WebException {
        if (!server.permissions().has(http.account().id(), at.marcelr.mradmin.Perms.PLAYER_VIEW)) {
            throw new Http.WebException(403, "no-permission");
        }
    }

    private static void fail(Object error) throws Http.WebException {
        if (error != null) {
            String key = String.valueOf(error);
            throw new Http.WebException(key.equals("not-found") ? 404 : 400, key);
        }
    }

    /** Free texts (reasons ...) at most 200 characters. */
    private static Map<String, Object> limit(Map<String, Object> args) {
        args.replaceAll((key, value) -> value instanceof String s && s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : value);
        return args;
    }

    /** What the action was about, for the protocol: "#3", a player's name, a world, a value. */
    private static String describe(Map<String, Object> args) {
        List<String> parts = new ArrayList<>();
        if (args.get("id") != null) {
            parts.add("#" + args.get("id"));
        }
        if (args.get("uuid") instanceof String uuid) {
            try {
                parts.add(name(UUID.fromString(uuid)));
            } catch (IllegalArgumentException e) {
                parts.add(uuid);
            }
        }
        for (String key : List.of("world", "kind", "value", "duration")) {
            if (args.get(key) != null && !String.valueOf(args.get(key)).isEmpty()) {
                parts.add(String.valueOf(args.get(key)));
            }
        }
        return String.join(" ", parts);
    }

    private static String name(UUID player) {
        String name = Bukkit.getOfflinePlayer(player).getName();
        return name == null ? player.toString() : name;
    }

    private void audit(Http http, String action, String detail) {
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), http.account().id(), http.account().name(),
                http.storedIp(), action, detail));
    }
}

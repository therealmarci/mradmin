package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.storage.Storage;

import java.util.HashMap;
import java.util.Map;

/**
 * MRpvp in the web interface - through MRpvp's own hook (version 4), so both plugins stay independent. The
 * permissions are MRpvp's own: mrpvp.admin.gui to look, mrpvp.admin.games for finished rounds with the saved
 * inventories, mrpvp.admin.stop / .ban / .reset / .restore / .review for the actions.
 * <pre>
 *  GET  /api/mrpvp/overview                       running rounds, invitations, arenas, slots, alerts
 *  GET  /api/mrpvp/live/{id}                      one running round (health, inventories, positions, map)
 *  GET  /api/mrpvp/games/{filter}/{offset}        finished rounds (filter all | win | draw | aborted)
 *  GET  /api/mrpvp/game/{id}                      one round with inventories, combat log, positions, map
 *  GET  /api/mrpvp/alerts                         conspicuous rounds of the last 24 h
 *  GET  /api/mrpvp/reviews                        rounds waiting for an admin (an older MRpvp without reviews: 404)
 *  (a player's part: GET /api/players/{uuid}/mrpvp, see PlayerRoutes)
 *  POST /api/mrpvp/action   {action, id | uuid, duration?, reason?}
 *       stop | ban | unban | reset | giveBack | count | discard (a waiting round)
 * </pre>
 * Live updates: MRpvp sends {@code event: plugin} ({"plugin":"mrpvp","type":"game","id":"..."}) through
 * MRadminApi.webEvent - the pages load again. Without MRpvp (or with integrations.mrpvp: false) everything answers
 * 404 "plugin-missing".
 */
public final class PvpRoutes {

    public static final String PERM_VIEW = "mrpvp.admin.gui";
    public static final String PERM_GAMES = "mrpvp.admin.games";
    public static final String PERM_STOP = "mrpvp.admin.stop";
    public static final String PERM_BAN = "mrpvp.admin.ban";
    public static final String PERM_RESET = "mrpvp.admin.reset";
    public static final String PERM_RESTORE = "mrpvp.admin.restore";
    public static final String PERM_REVIEW = "mrpvp.admin.review";
    /** action -> the permission it needs */
    private static final Map<String, String> ACTIONS = Map.of(
            "stop", PERM_STOP, "ban", PERM_BAN, "unban", PERM_BAN, "reset", PERM_RESET, "giveBack", PERM_RESTORE,
            "count", PERM_REVIEW, "discard", PERM_REVIEW);
    private static final int MAX_REASON = 200;

    private final WebServer server;
    private final MRAdmin plugin;

    private PvpRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        PvpRoutes r = new PvpRoutes(server);
        server.route("GET", "/api/mrpvp/overview", http -> r.get(http, PERM_VIEW, "overview", Map.of()));
        server.route("GET", "/api/mrpvp/alerts", http -> r.get(http, PERM_VIEW, "alerts", Map.of()));
        server.route("GET", "/api/mrpvp/reviews", http -> r.get(http, PERM_REVIEW, "reviews", Map.of()));
        server.route("GET", "/api/mrpvp/live/{id}", http -> r.get(http, PERM_VIEW, "live", Map.of("id", id(http))));
        server.route("GET", "/api/mrpvp/games/{filter}/{offset}", http -> r.get(http, PERM_GAMES, "games",
                Map.of("filter", http.param("filter"), "offset", number(http.param("offset")))));
        server.route("GET", "/api/mrpvp/game/{id}", http -> r.get(http, PERM_GAMES, "game", Map.of("id", id(http))));
        server.route("POST", "/api/mrpvp/action", r::action);
    }

    private static long id(Http http) throws Http.WebException {
        long id = number(http.param("id"));
        if (id <= 0) {
            throw new Http.WebException(400, "invalid-id");
        }
        return id;
    }

    private static long number(String value) throws Http.WebException {
        try {
            return value == null ? 0 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new Http.WebException(400, "invalid-number");
        }
    }

    /** The MRpvp button if MRpvp runs, is connected and offers web data; otherwise null. */
    public static Settings.HubButton hook(MRAdmin plugin) {
        return plugin.settings().adminMode().buttons().stream()
                .filter(b -> b.id().equals("mrpvp") && plugin.hooks().available(b) && plugin.hooks().hasWeb(b))
                .findFirst().orElse(null);
    }

    private Settings.HubButton require(Http http, String permission) throws Http.WebException {
        Settings.HubButton hook = hook(plugin);
        if (hook == null) {
            throw new Http.WebException(404, "plugin-missing");
        }
        if (!server.permissions().has(http.account().id(), PERM_VIEW)
                || !server.permissions().has(http.account().id(), permission)) {
            throw new Http.WebException(403, "no-permission");
        }
        return hook;
    }

    private void get(Http http, String permission, String request, Map<String, Object> args) throws Exception {
        Settings.HubButton hook = require(http, permission);
        Object answer = server.sync(() -> plugin.hooks().web(hook, request, args));
        if (answer == null) {
            throw new Http.WebException(404, "not-found");
        }
        http.json(200, answer);
    }

    private void action(Http http) throws Exception {
        String action = http.string("action");
        String permission = action == null ? null : ACTIONS.get(action);
        if (permission == null) {
            throw new Http.WebException(400, "unknown-action");
        }
        Settings.HubButton hook = require(http, permission);

        Map<String, Object> args = new HashMap<>();
        args.put("action", action);
        args.put("admin", http.account().name());
        String uuid = http.string("uuid");
        String id = http.string("id");
        if (uuid != null) {
            args.put("uuid", uuid);
        }
        if (id != null) {
            args.put("id", number(id));
        }
        String duration = http.string("duration");
        if (duration != null) {
            args.put("duration", number(duration));
        }
        String reason = http.string("reason");
        if (reason != null) {
            args.put("reason", reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) : reason);
        }

        Object error = server.sync(() -> plugin.hooks().web(hook, "action", args));
        if (error != null) {
            throw new Http.WebException(400, String.valueOf(error));
        }
        String detail = id != null ? "#" + id : uuid == null ? "" : playerName(uuid);
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), http.account().id(), http.account().name(),
                http.storedIp(), "pvp-" + action, detail));
    }

    /** For the log: the player's name (MRpvp accepted the UUID, so it is valid). */
    private static String playerName(String uuid) {
        try {
            String name = org.bukkit.Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuid)).getName();
            return name == null ? uuid : name;
        } catch (IllegalArgumentException e) {
            return uuid;
        }
    }
}

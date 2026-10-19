package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.storage.Storage;

import java.util.HashMap;
import java.util.Map;

/**
 * MRsleep in the web interface - through MRsleep's own hook (version 4), so both plugins stay independent. The
 * permissions are MRsleep's own: mrsleep.admin.gui to look, mrsleep.admin.clear to lift /nosleep blocks,
 * mrsleep.admin.bypass for cooldowns and bypasses, mrsleep.admin.ban to exclude players from /nosleep.
 * <pre>
 *  GET  /api/mrsleep/overview
 *  (a player's part: GET /api/players/{uuid}/mrsleep, see PlayerRoutes)
 *  POST /api/mrsleep/action   {action, uuid | world, duration?, reason?}
 *       clear | release | bypass | removeBypass | resetCooldown | ban | unban
 * </pre>
 * Without MRsleep (or with integrations.mrsleep: false) everything answers 404 "plugin-missing".
 */
public final class SleepRoutes {

    public static final String PERM_VIEW = "mrsleep.admin.gui";
    public static final String PERM_CLEAR = "mrsleep.admin.clear";
    public static final String PERM_BYPASS = "mrsleep.admin.bypass";
    public static final String PERM_BAN = "mrsleep.admin.ban";
    /** action -> the permission it needs */
    private static final Map<String, String> ACTIONS = Map.of(
            "clear", PERM_CLEAR, "release", PERM_CLEAR,
            "bypass", PERM_BYPASS, "removeBypass", PERM_BYPASS, "resetCooldown", PERM_BYPASS,
            "ban", PERM_BAN, "unban", PERM_BAN);
    private static final int MAX_REASON = 200;

    private final WebServer server;
    private final MRAdmin plugin;

    private SleepRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        SleepRoutes r = new SleepRoutes(server);
        server.route("GET", "/api/mrsleep/overview", http -> r.get(http, "overview"));
        server.route("POST", "/api/mrsleep/action", r::action);
    }

    /** The MRsleep button if MRsleep runs, is connected and offers web data; otherwise null. */
    public static Settings.HubButton hook(MRAdmin plugin) {
        return plugin.settings().adminMode().buttons().stream()
                .filter(b -> b.id().equals("mrsleep") && plugin.hooks().available(b) && plugin.hooks().hasWeb(b))
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

    private void get(Http http, String request) throws Exception {
        Settings.HubButton hook = require(http, PERM_VIEW);
        Object answer = server.sync(() -> plugin.hooks().web(hook, request, Map.of()));
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
        // Looking is needed too - an action never works on a page the member cannot see.
        require(http, PERM_VIEW);
        Settings.HubButton hook = require(http, permission);

        Map<String, Object> args = new HashMap<>();
        args.put("action", action);
        args.put("admin", http.account().name());
        String uuid = http.string("uuid");
        String world = http.string("world");
        if (uuid != null) {
            args.put("uuid", uuid);
        }
        if (world != null) {
            args.put("world", world);
        }
        String duration = http.string("duration");
        if (duration != null) {
            try {
                args.put("duration", Long.parseLong(duration));
            } catch (NumberFormatException e) {
                throw new Http.WebException(400, "invalid-duration");
            }
        }
        String reason = http.string("reason");
        if (reason != null) {
            args.put("reason", reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) : reason);
        }

        Object error = server.sync(() -> plugin.hooks().web(hook, "action", args));
        if (error != null) {
            throw new Http.WebException(400, String.valueOf(error));
        }
        String detail = world != null ? world : uuid == null ? "" : playerName(uuid);
        if (duration != null) {
            detail += " " + duration + " ms";
        }
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), http.account().id(), http.account().name(),
                http.storedIp(), "sleep-" + action, detail));
    }

    /** For the log: the player's name (MRsleep accepted the UUID, so it is valid and known). */
    private static String playerName(String uuid) {
        try {
            String name = org.bukkit.Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuid)).getName();
            return name == null ? uuid : name;
        } catch (IllegalArgumentException e) {
            return uuid;
        }
    }
}

package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.storage.Storage;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The web console: live log (mradmin.web.console.view) and commands (mradmin.web.console.execute). Both permissions are
 * in no bundle. Before anything is shown, the member confirms with a fresh code from their authenticator app; that
 * unlocks this one browser session for web.console.unlock-minutes. Five wrong codes lock the console for the account
 * for 15 minutes. Commands run as the console; blocked ones (web.console.blocked) never - also not inside
 * "execute ... run". Every command goes into the web log and the Discord log (web-console).
 * <pre>
 *  GET  /api/console/state              enabled, unlocked until, may execute
 *  POST /api/console/unlock  {code}
 *  POST /api/console/lock
 *  GET  /api/console/log?after=n        lines after n (unlocked only)
 *  POST /api/console/command {command}  (unlocked, mradmin.web.console.execute)
 * </pre>
 */
public final class ConsoleRoutes {

    private static final int MAX_LINES = 500;
    private static final int MAX_COMMAND = 500;
    private static final int MAX_FAILURES = 5;
    private static final long FAILURE_LOCK_MILLIS = 15 * 60_000L;

    private final WebServer server;
    private final MRAdmin plugin;
    private final ConsoleLog log;
    /** session id -> unlocked until (epoch ms) */
    private final Map<String, Long> unlocked = new ConcurrentHashMap<>();
    /** account -> wrong codes in a row, and locked until */
    private final Map<UUID, int[]> failures = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lockedUntil = new ConcurrentHashMap<>();

    private ConsoleRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
        this.log = new ConsoleLog(plugin);
    }

    public static void register(WebServer server) {
        ConsoleRoutes r = new ConsoleRoutes(server);
        server.route("GET", "/api/console/state", r::state);
        server.route("POST", "/api/console/unlock", r::unlock);
        server.route("POST", "/api/console/lock", r::lock);
        server.route("GET", "/api/console/log", r::lines);
        server.route("POST", "/api/console/command", r::command);
        // The log reader runs while the web interface runs (and web.console.enabled is on).
        server.lifecycle(() -> {
            if (enabled(r.plugin)) {
                r.log.start();
            }
        }, r.log::stop);
    }

    static boolean enabled(MRAdmin plugin) {
        return plugin.getConfig().getBoolean("web.console.enabled", true);
    }

    private boolean may(Http http, String permission) {
        return server.permissions().has(http.account().id(), permission);
    }

    /** Switched on and allowed to look - otherwise 404 / 403. */
    private void require(Http http) throws Http.WebException {
        if (!enabled(plugin)) {
            throw new Http.WebException(404, "feature-disabled");
        }
        if (!may(http, Perms.WEB_CONSOLE_VIEW)) {
            throw new Http.WebException(403, "no-permission");
        }
    }

    private long until(Http http) {
        Long until = unlocked.get(http.session().id());
        if (until == null || until <= System.currentTimeMillis()) {
            unlocked.remove(http.session().id());
            return 0;
        }
        return until;
    }

    private void requireUnlocked(Http http) throws Http.WebException {
        require(http);
        if (until(http) == 0) {
            throw new Http.WebException(403, "console-locked");
        }
    }

    // ------------------------------------------------------------------ state

    private void state(Http http) throws Exception {
        require(http);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unlocked", until(http) > 0);
        result.put("until", until(http));
        result.put("execute", may(http, Perms.WEB_CONSOLE_EXECUTE));
        long locked = lockedUntil.getOrDefault(http.account().id(), 0L);
        result.put("lockedUntil", locked > System.currentTimeMillis() ? locked : 0);
        http.json(200, result);
    }

    private void unlock(Http http) throws Exception {
        require(http);
        UUID account = http.account().id();
        long now = System.currentTimeMillis();
        if (lockedUntil.getOrDefault(account, 0L) > now) {
            throw new Http.WebException(429, "console-code-locked");
        }
        if (!server.confirmed(http)) {
            int[] count = failures.computeIfAbsent(account, k -> new int[1]);
            if (++count[0] >= MAX_FAILURES) {
                count[0] = 0;
                lockedUntil.put(account, now + FAILURE_LOCK_MILLIS);
                audit(http, "console-locked", "5 wrong codes");
            }
            throw new Http.WebException(400, "code-wrong");
        }
        failures.remove(account);
        int minutes = Math.clamp(plugin.getConfig().getInt("web.console.unlock-minutes", 15), 1, 240);
        unlocked.put(http.session().id(), now + minutes * 60_000L);
        audit(http, "console-unlock", minutes + " min");
        http.json(200, Map.of("until", now + minutes * 60_000L));
    }

    private void lock(Http http) throws Exception {
        require(http);
        unlocked.remove(http.session().id());
        http.json(200, Map.of());
    }

    // -------------------------------------------------------------------- log

    private void lines(Http http) throws Exception {
        requireUnlocked(http);
        String query = http.exchange().getRequestURI().getQuery();
        long after = 0;
        if (query != null && query.startsWith("after=")) {
            try {
                after = Long.parseLong(query.substring(6));
            } catch (NumberFormatException ignored) {
                // from the start
            }
        }
        List<Map<String, Object>> lines = new ArrayList<>();
        for (ConsoleLog.Line line : log.after(after, MAX_LINES)) {
            lines.add(Map.of("seq", line.seq(), "text", line.text()));
        }
        http.json(200, Map.of("lines", lines, "last", log.last()));
    }

    // ---------------------------------------------------------------- command

    private void command(Http http) throws Exception {
        requireUnlocked(http);
        if (!may(http, Perms.WEB_CONSOLE_EXECUTE)) {
            throw new Http.WebException(403, "no-permission");
        }
        String raw = http.string("command");
        String command = raw == null ? "" : raw.strip();
        if (command.startsWith("/")) {
            command = command.substring(1).strip();
        }
        if (command.isEmpty() || command.length() > MAX_COMMAND || command.contains("\n") || command.contains("\r")) {
            throw new Http.WebException(400, "command-invalid");
        }
        String blockedBy = at.marcelr.mradmin.util.CommandFilter.blocked(command, plugin.getConfig().getStringList("web.console.blocked"));
        if (blockedBy != null) {
            audit(http, "console-blocked", command);
            throw new Http.WebException(403, "command-blocked");
        }
        String name = http.account().name();
        String run = command;
        server.sync(() -> {
            plugin.getLogger().info(name + " (web console): " + run);
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run);
        });
        audit(http, "console-command", command);
        plugin.discord().log("web-console", "player", name, "command", command.replace('`', '\''));
        http.json(200, Map.of());
    }

    private void audit(Http http, String action, String detail) {
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), http.account().id(), http.account().name(),
                http.storedIp(), action, detail));
    }
}

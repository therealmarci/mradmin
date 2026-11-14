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
 *  GET  /api/console/tail?after=n       page Log: live lines with level (mradmin.web.console.view, no unlocking)
 *  POST /api/console/command {command}  (unlocked, mradmin.web.console.execute)
 * </pre>
 * With {@code server=room} log, tail and command are about the waiting room server (through its agent, RoomAgent) -
 * the same permissions, the same unlocking (one per browser session), the same block list; links and IP addresses
 * are hidden here like for this server. Its own switch agent.console-commands can forbid commands there.
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
        server.route("GET", "/api/console/tail", r::tail);
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

    /** The page Console (admins: mradmin.web.console.execute) - switched on and allowed, otherwise 404 / 403. */
    private void require(Http http) throws Http.WebException {
        requireView(http);
        if (!may(http, Perms.WEB_CONSOLE_EXECUTE)) {
            throw new Http.WebException(403, "no-permission");
        }
    }

    /** Reading the console output on the page Log (mradmin.web.console.view). */
    private void requireView(Http http) throws Http.WebException {
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

    /** Whether the request is about the waiting room server (?server=room). */
    private boolean room(Http http) throws Http.WebException {
        if (!"room".equals(http.query().get("server"))) {
            return false;
        }
        if (!plugin.waitingRoom().active()) {
            throw new Http.WebException(404, "no-waitroom");
        }
        return true;
    }

    /** Console lines of the waiting room after {@code after}, cleaned like ours: [seq, level, text]. */
    private Map<String, Object> roomLines(long after, boolean withLevel) throws Http.WebException {
        try {
            var o = com.google.gson.JsonParser.parseString(plugin.roomAgent().get("/agent/console?after=" + after)).getAsJsonObject();
            boolean links = plugin.getConfig().getBoolean("web.console.hide-links", true);
            boolean ips = plugin.getConfig().getBoolean("web.console.hide-ips", true);
            List<Map<String, Object>> lines = new ArrayList<>();
            for (var element : o.getAsJsonArray("lines")) {
                var l = element.getAsJsonObject();
                String text = ConsoleLog.clean(l.get("text").getAsString(), links, ips);
                lines.add(withLevel ? Map.of("seq", l.get("seq").getAsLong(), "level", l.get("level").getAsString(), "text", text)
                        : Map.of("seq", l.get("seq").getAsLong(), "text", text));
            }
            return Map.of("lines", lines, "last", o.get("last").getAsLong());
        } catch (java.io.IOException | RuntimeException e) {
            throw new Http.WebException(503, "waitroom-offline");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Http.WebException(503, "waitroom-offline");
        }
    }

    private void lines(Http http) throws Exception {
        requireUnlocked(http);
        if (room(http)) {
            http.json(200, roomLines(number(http.query().get("after")), false));
            return;
        }
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

    /**
     * The page Log: new console lines after {@code after} (like /log, but without unlocking), with their level - a
     * line without one (stack trace) keeps the one before. {@code after} very large = only the number of the newest.
     */
    private void tail(Http http) throws Exception {
        requireView(http);
        if (room(http)) {
            http.json(200, roomLines(number(http.query().get("after")), true));
            return;
        }
        long after = number(http.query().get("after"));
        List<Map<String, Object>> lines = new ArrayList<>();
        String level = "info";
        for (ConsoleLog.Line line : log.after(after, MAX_LINES)) {
            String own = ServerLogFiles.level(line.text());
            level = own == null ? level : own;
            lines.add(Map.of("seq", line.seq(), "level", level, "text", line.text()));
        }
        http.json(200, Map.of("lines", lines, "last", log.last()));
    }

    private static long number(String value) {
        try {
            return value == null || value.isBlank() ? 0 : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
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
        if (room(http)) {
            com.google.gson.JsonObject body = new com.google.gson.JsonObject();
            body.addProperty("command", command);
            body.addProperty("by", name);
            int status;
            try {
                status = plugin.roomAgent().post("/agent/command", body.toString());
            } catch (java.io.IOException e) {
                throw new Http.WebException(503, "waitroom-offline");
            }
            if (status == 403) {
                throw new Http.WebException(403, "waitroom-commands-off");
            }
            if (status != 200) {
                throw new Http.WebException(503, "waitroom-offline");
            }
            audit(http, "console-command", "[waitroom] " + command);
            plugin.discord().log("web-console", "player", name, "command", ("[waitroom] " + command).replace('`', '\''));
            http.json(200, Map.of());
            return;
        }
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

package at.marcelr.mradmin.web;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.storage.Storage;
import at.marcelr.mradmin.ticket.Ticket;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static at.marcelr.mradmin.Messages.tag;

/**
 * Player management: everyone who has been on the server, and per player what MRadmin and the connected plugins know
 * and can do. The list and the profile need mradmin.player.view; every part has its own permission on top (tickets:
 * mradmin.tickets.view, freezing: mradmin.player.freeze, kicking: mradmin.player.kick, MRsleep and MRchunks: their
 * own). The plugins answer through their hook (version 4) - without them their part is simply missing.
 * <pre>
 *  GET  /api/players?q=&amp;filter=all|online|flagged&amp;page=0   50 per page, online first, then by last visit
 *  GET  /api/players/{uuid}                                profile, tickets, which plugin parts there are
 *  POST /api/players/{uuid}/freeze | unfreeze
 *  POST /api/players/{uuid}/kick   {reason?}
 *  GET  /api/players/{uuid}/mrsleep                        MRsleep's part (its actions: POST /api/mrsleep/action)
 *  GET  /api/players/{uuid}/mrchunks                       MRchunks' part
 *  POST /api/players/{uuid}/mrchunks   {action, kind?, value?, reason?}   limit | ban | unban | deleteAll
 * </pre>
 * No IP addresses - they are personal data and not needed here.
 */
public final class PlayerRoutes {

    private static final int PAGE = 50;
    /** The list of all players reads the player files - at most once a minute. */
    private static final long LIST_CACHE_MILLIS = 60_000L;
    private static final int MAX_REASON = 200;
    /** MRchunks' player actions -> MRchunks' permission for them. */
    private static final Map<String, String> CHUNK_ACTIONS = Map.of(
            "limit", ChunkRoutes.PERM_LIMIT, "ban", ChunkRoutes.PERM_BAN, "unban", ChunkRoutes.PERM_BAN,
            "deleteAll", ChunkRoutes.PERM_MANAGE);

    private final WebServer server;
    private final MRAdmin plugin;
    private volatile List<Known> known = List.of();
    private volatile long knownTime;

    /** A player who has been on the server. */
    private record Known(UUID id, String name, long firstPlayed, long lastSeen) {
    }

    private PlayerRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        PlayerRoutes r = new PlayerRoutes(server);
        server.route("GET", "/api/players", r::list);
        server.route("GET", "/api/players/{uuid}", r::profile);
        server.route("POST", "/api/players/{uuid}/freeze", http -> r.freeze(http, true));
        server.route("POST", "/api/players/{uuid}/unfreeze", http -> r.freeze(http, false));
        server.route("POST", "/api/players/{uuid}/kick", r::kick);
        server.route("GET", "/api/players/{uuid}/mrsleep", http -> r.part(http, SleepRoutes.hook(r.plugin), SleepRoutes.PERM_VIEW));
        server.route("GET", "/api/players/{uuid}/mrchunks", http -> r.part(http, ChunkRoutes.hook(r.plugin), ChunkRoutes.PERM_VIEW));
        server.route("POST", "/api/players/{uuid}/mrchunks", r::chunksAction);
    }

    private boolean may(Http http, String permission) {
        return server.permissions().has(http.account().id(), permission);
    }

    private void require(Http http, String permission) throws Http.WebException {
        if (!may(http, permission)) {
            throw new Http.WebException(403, "no-permission");
        }
    }

    private static UUID uuid(Http http) throws Http.WebException {
        try {
            return UUID.fromString(http.param("uuid"));
        } catch (IllegalArgumentException e) {
            throw new Http.WebException(404, "not-found");
        }
    }

    // ------------------------------------------------------------------- list

    private void list(Http http) throws Exception {
        require(http, Perms.PLAYER_VIEW);
        Map<String, String> query = query(http);
        String q = query.getOrDefault("q", "").trim().toLowerCase(Locale.ROOT);
        String filter = query.getOrDefault("filter", "all");
        int page = Math.max(0, parseInt(query.get("page")));

        List<Known> all = known();
        // Permissions first - asking LuckPerms must not happen on the server thread.
        boolean tickets = may(http, Perms.TICKETS_VIEW);
        boolean sleep = may(http, SleepRoutes.PERM_VIEW);
        boolean chunks = may(http, ChunkRoutes.PERM_VIEW);
        Map<String, Object> result = server.sync(() -> {
            Map<String, List<String>> flags = flags(tickets, sleep, chunks);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Known k : all) {
                boolean online = Bukkit.getPlayer(k.id()) != null;
                List<String> f = flags.getOrDefault(k.id().toString(), List.of());
                if (!q.isEmpty() && !k.name().toLowerCase(Locale.ROOT).contains(q)
                        || filter.equals("online") && !online || filter.equals("flagged") && f.isEmpty()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("uuid", k.id().toString());
                row.put("name", k.name());
                row.put("online", online);
                row.put("lastSeen", online ? System.currentTimeMillis() : k.lastSeen());
                row.put("flags", f);
                rows.add(row);
            }
            rows.sort(Comparator.comparing((Map<String, Object> r) -> !(Boolean) r.get("online"))
                    .thenComparing(r -> -(Long) r.get("lastSeen")));
            Map<String, Object> answer = new LinkedHashMap<>();
            answer.put("total", rows.size());
            answer.put("page", page);
            answer.put("pageSize", PAGE);
            answer.put("players", rows.subList(Math.min(rows.size(), page * PAGE), Math.min(rows.size(), (page + 1) * PAGE)));
            return answer;
        });
        http.json(200, result);
    }

    /** Everyone with a name who has been on the server (cached), plus whoever is online now. */
    private List<Known> known() throws Exception {
        if (System.currentTimeMillis() - knownTime > LIST_CACHE_MILLIS) {
            known = server.sync(() -> {
                Map<UUID, Known> map = new LinkedHashMap<>();
                for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                    if (p.getName() != null) {
                        map.put(p.getUniqueId(), new Known(p.getUniqueId(), p.getName(), p.getFirstPlayed(), p.getLastSeen()));
                    }
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    map.putIfAbsent(p.getUniqueId(), new Known(p.getUniqueId(), p.getName(), p.getFirstPlayed(), System.currentTimeMillis()));
                }
                return List.copyOf(map.values());
            });
            knownTime = System.currentTimeMillis();
        }
        return known;
    }

    /**
     * Marks per player (uuid -> keys) - only from the parts the member may see: frozen, reported (open tickets about
     * them), and what MRsleep / MRchunks report through their hook ("flags").
     */
    private Map<String, List<String>> flags(boolean tickets, boolean sleep, boolean chunks) {
        Map<String, List<String>> flags = new HashMap<>();
        if (plugin.feature(Feature.FREEZE)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plugin.freeze().frozen(p.getUniqueId())) {
                    flags.computeIfAbsent(p.getUniqueId().toString(), k -> new ArrayList<>()).add("frozen");
                }
            }
        }
        if (plugin.feature(Feature.TICKETS) && tickets) {
            for (Ticket t : plugin.tickets().list(null, true, null)) {
                if (t.target() != null) {
                    List<String> f = flags.computeIfAbsent(t.target().toString(), k -> new ArrayList<>());
                    if (!f.contains("reported")) {
                        f.add("reported");
                    }
                }
            }
        }
        addFlags(flags, SleepRoutes.hook(plugin), sleep);
        addFlags(flags, ChunkRoutes.hook(plugin), chunks);
        return flags;
    }

    private void addFlags(Map<String, List<String>> flags, Settings.HubButton hook, boolean allowed) {
        if (hook == null || !allowed || !(plugin.hooks().web(hook, "flags", Map.of()) instanceof Map<?, ?> answer)) {
            return;
        }
        answer.forEach((uuid, list) -> {
            if (list instanceof List<?> l) {
                List<String> f = flags.computeIfAbsent(String.valueOf(uuid), k -> new ArrayList<>());
                l.forEach(v -> f.add(String.valueOf(v)));
            }
        });
    }

    // ---------------------------------------------------------------- profile

    private void profile(Http http) throws Exception {
        require(http, Perms.PLAYER_VIEW);
        UUID id = uuid(http);
        boolean tickets = may(http, Perms.TICKETS_VIEW);
        boolean freeze = plugin.feature(Feature.FREEZE) && may(http, Perms.PLAYER_FREEZE);
        boolean kick = may(http, Perms.PLAYER_KICK);
        boolean sleep = may(http, SleepRoutes.PERM_VIEW);
        boolean chunks = may(http, ChunkRoutes.PERM_VIEW);
        Map<String, Object> result = server.sync(() -> {
            OfflinePlayer p = Bukkit.getOfflinePlayer(id);
            Player online = p.getPlayer();
            if (online == null && !p.hasPlayedBefore()) {
                return null;
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("uuid", id.toString());
            r.put("name", online != null ? online.getName() : p.getName() == null ? id.toString().substring(0, 8) : p.getName());
            r.put("online", online != null);
            r.put("firstPlayed", p.getFirstPlayed());
            r.put("lastSeen", online != null ? System.currentTimeMillis() : p.getLastSeen());
            r.put("playtime", playtime(p));
            if (online != null) {
                Location l = online.getLocation();
                r.put("world", l.getWorld().getName());
                r.put("x", l.getBlockX());
                r.put("y", l.getBlockY());
                r.put("z", l.getBlockZ());
                r.put("gamemode", online.getGameMode().name().toLowerCase(Locale.ROOT));
                r.put("ping", online.getPing());
                r.put("health", Math.round(online.getHealth()));
                r.put("food", online.getFoodLevel());
                r.put("level", online.getLevel());
                r.put("adminMode", plugin.adminMode().active(online));
            }
            r.put("frozen", plugin.feature(Feature.FREEZE) && plugin.freeze().frozen(id));

            if (plugin.feature(Feature.TICKETS) && tickets) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (boolean open : new boolean[] {true, false}) {
                    for (Ticket t : plugin.tickets().list(null, open, null)) {
                        boolean creator = t.isCreator(id);
                        if (creator || id.equals(t.target())) {
                            list.add(Map.of("id", t.id(), "summary", plugin.tickets().summary(t),
                                    "category", plugin.tickets().categoryName(t.category()), "open", t.open(),
                                    "role", creator ? "creator" : "target", "lastActivity", t.lastActivity().toEpochMilli()));
                        }
                    }
                }
                r.put("tickets", list);
            }

            // What the member may do here, and which plugin parts the page can load.
            Map<String, Object> may = new LinkedHashMap<>();
            may.put("freeze", freeze);
            may.put("kick", kick);
            r.put("may", may);
            Map<String, Object> parts = new LinkedHashMap<>();
            parts.put("mrsleep", SleepRoutes.hook(plugin) != null && sleep);
            parts.put("mrchunks", ChunkRoutes.hook(plugin) != null && chunks);
            r.put("parts", parts);
            return r;
        });
        if (result == null) {
            throw new Http.WebException(404, "not-found");
        }
        http.json(200, result);
    }

    /** Play time in ms from the statistics (also offline - Paper reads the player's stats file). -1 = unknown. */
    private static long playtime(OfflinePlayer p) {
        try {
            return p.getStatistic(Statistic.PLAY_ONE_MINUTE) * 50L;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------- actions

    private void freeze(Http http, boolean freeze) throws Exception {
        require(http, Perms.PLAYER_VIEW);
        require(http, Perms.PLAYER_FREEZE);
        if (!plugin.feature(Feature.FREEZE)) {
            throw new Http.WebException(404, "feature-disabled");
        }
        UUID id = uuid(http);
        if (id.equals(http.account().id())) {
            throw new Http.WebException(400, "not-yourself");
        }
        String error = server.sync(() -> {
            Player target = Bukkit.getPlayer(id);
            if (target == null) {
                return "player-offline";
            }
            return plugin.freeze().set(http.account().name(), null, target, freeze) ? null
                    : freeze ? "already-frozen" : "not-frozen";
        });
        if (error != null) {
            throw new Http.WebException(400, error);
        }
        audit(http, freeze ? "player-freeze" : "player-unfreeze", name(id));
    }

    private void kick(Http http) throws Exception {
        require(http, Perms.PLAYER_VIEW);
        require(http, Perms.PLAYER_KICK);
        UUID id = uuid(http);
        if (id.equals(http.account().id())) {
            throw new Http.WebException(400, "not-yourself");
        }
        String raw = http.string("reason");
        String reason = raw == null || raw.isBlank() ? null : raw.strip();
        if (reason != null && reason.length() > MAX_REASON) {
            reason = reason.substring(0, MAX_REASON);
        }
        String given = reason;
        String staff = http.account().name();
        String error = server.sync(() -> {
            Player target = Bukkit.getPlayer(id);
            if (target == null) {
                return "player-offline";
            }
            Messages messages = plugin.messages();
            String text = given != null ? given : messages.as(target, () -> messages.plain("kick-no-reason"));
            target.kick(messages.as(target, () -> messages.get("kick-screen", tag("staff", staff), tag("reason", text))));
            plugin.getLogger().info(staff + " (web) kicked " + target.getName() + ": " + text);
            plugin.discord().log("player-kick", "admin", staff, "player", target.getName(), "reason", text);
            return null;
        });
        if (error != null) {
            throw new Http.WebException(400, error);
        }
        audit(http, "player-kick", name(id) + (reason == null ? "" : " - " + reason));
    }

    // ---------------------------------------------------------- plugin parts

    /** A plugin's part of the profile (its "player" answer), with its own view permission. */
    private void part(Http http, Settings.HubButton hook, String permission) throws Exception {
        require(http, Perms.PLAYER_VIEW);
        if (hook == null) {
            throw new Http.WebException(404, "plugin-missing");
        }
        require(http, permission);
        UUID id = uuid(http);
        Object answer = server.sync(() -> plugin.hooks().web(hook, "player", Map.of("uuid", id.toString())));
        if (answer == null) {
            throw new Http.WebException(404, "not-found");
        }
        http.json(200, answer);
    }

    /** MRchunks' player actions, each with MRchunks' own permission (and mrchunks.admin.gui to see the part at all). */
    private void chunksAction(Http http) throws Exception {
        require(http, Perms.PLAYER_VIEW);
        Settings.HubButton hook = ChunkRoutes.hook(plugin);
        if (hook == null) {
            throw new Http.WebException(404, "plugin-missing");
        }
        String action = http.string("action");
        String permission = action == null ? null : CHUNK_ACTIONS.get(action);
        if (permission == null) {
            throw new Http.WebException(400, "unknown-action");
        }
        require(http, ChunkRoutes.PERM_VIEW);
        require(http, permission);
        UUID id = uuid(http);
        String kind = http.string("kind");
        String value = http.string("value");
        String reason = http.string("reason");
        Map<String, Object> args = new HashMap<>();
        if (reason != null) {
            args.put("reason", reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) : reason);
        }
        args.put("action", action);
        args.put("uuid", id.toString());
        args.put("admin", http.account().name());
        if (kind != null) {
            args.put("kind", kind);
        }
        if (value != null) {
            args.put("value", value);
        }
        Object error = server.sync(() -> plugin.hooks().web(hook, "playerAction", args));
        if (error != null) {
            throw new Http.WebException(400, String.valueOf(error));
        }
        audit(http, "chunkloader-" + action, name(id) + (kind == null ? "" : " " + kind) + (value == null ? "" : " " + value));
    }

    // ---------------------------------------------------------------- helpers

    private void audit(Http http, String action, String detail) {
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), http.account().id(), http.account().name(),
                http.storedIp(), action, detail));
    }

    private static String name(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? id.toString() : name;
    }

    private static Map<String, String> query(Http http) {
        Map<String, String> result = new HashMap<>();
        String raw = http.exchange().getRequestURI().getRawQuery();
        if (raw == null) {
            return result;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                result.put(java.net.URLDecoder.decode(pair.substring(0, eq), java.nio.charset.StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    private static int parseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

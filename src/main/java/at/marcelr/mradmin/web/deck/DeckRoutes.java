package at.marcelr.mradmin.web.deck;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.api.AdminModule;
import at.marcelr.mradmin.api.DeckTile;
import at.marcelr.mradmin.api.SelfViewer;
import at.marcelr.mradmin.api.WebModule;
import at.marcelr.mradmin.mode.AdminModeManager;
import at.marcelr.mradmin.storage.Storage;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.web.Http;
import at.marcelr.mradmin.web.PanelRoutes;
import at.marcelr.mradmin.web.WebServer;
import at.marcelr.mradmin.web.auth.WebPermissions;
import at.marcelr.mradmin.web.self.SelfRoutes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The interface of the Stream Deck plugin of MRadmin.
 * <ul>
 *   <li>Connecting (no sign-in): {@code GET /api/deck/hello} (is this an MRadmin, with the server's name),
 *   {@code POST /api/deck/link} (a code for a new Stream Deck), {@code POST /api/deck/link/poll} (confirmed yet?).</li>
 *   <li>The own account in the browser: {@code GET /api/deck/link/info?code=}, {@code POST /api/deck/link/confirm},
 *   {@code GET /api/deck/devices}, {@code POST /api/deck/devices/{id}/revoke}, {@code POST /api/deck/keys}.</li>
 *   <li>The Stream Deck with its key: {@code GET /api/deck/state} (everything its keys show, only what the player may
 *   see in the web interface as well), {@code POST /api/deck/press} (a key with an effect), {@code POST
 *   /api/deck/logout}.</li>
 * </ul>
 * Effects check the same permissions as the web interface; team keys need the team permission and an own sign-in
 * like the administration of the web interface.
 */
public final class DeckRoutes {

    /** The state of one Stream Deck is built at most this often (many keys ask at once). */
    private static final long CACHE_MILLIS = 2_000L;

    private final WebServer server;
    private final MRAdmin plugin;
    private final SelfRoutes self;
    /** device id -> time of the newest staff chat message the Stream Deck has seen */
    private final Map<String, Long> staffSeen = new ConcurrentHashMap<>();
    /** device id + language -> [built at, state] */
    private final Map<String, Object[]> cache = new ConcurrentHashMap<>();

    private DeckRoutes(WebServer server, SelfRoutes self) {
        this.server = server;
        this.plugin = server.plugin();
        this.self = self;
    }

    public static void register(WebServer server, SelfRoutes self) {
        DeckRoutes r = new DeckRoutes(server, self);
        server.open("GET", "/api/deck/hello", r::hello);
        server.open("POST", "/api/deck/link", r::link);
        server.open("POST", "/api/deck/link/poll", r::poll);
        server.account("GET", "/api/deck/link/info", r::info);
        server.account("POST", "/api/deck/link/confirm", r::confirm);
        server.account("GET", "/api/deck/devices", r::devices);
        server.account("POST", "/api/deck/devices/{id}/revoke", r::revoke);
        server.account("POST", "/api/deck/keys", r::key);
        server.deck("GET", "/api/deck/state", r::state);
        server.deck("POST", "/api/deck/press", r::press);
        server.deck("POST", "/api/deck/logout", r::logout);
    }

    private DeckDevices devices() {
        return server.deckDevices();
    }

    private boolean may(UUID player, String permission) {
        return server.permissions().has(player, permission);
    }

    /** Team keys: the team permission plus an own sign-in, like the administration of the web interface. */
    private boolean team(UUID player) {
        return may(player, WebPermissions.PERM_WEB) && server.auth().teamReady(player);
    }

    private void requireOn() throws Http.WebException {
        if (!plugin.settings().deck().enabled()) {
            throw new Http.WebException(404, "deck-off");
        }
    }

    // ------------------------------------------------------------ connecting

    private void hello(Http http) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mradmin", true);
        result.put("enabled", plugin.settings().deck().enabled());
        result.put("name", plugin.branding().name());
        result.put("url", plugin.settings().web().publicUrl());
        http.json(200, result);
    }

    private void link(Http http) throws Exception {
        requireOn();
        if (!server.allow("deck-link:" + http.ip(), 6)) {
            throw new Http.WebException(429, "rate-limit");
        }
        DeckDevices.Pending p = devices().start(http.string("name"), http.storedIp());
        if (p == null) {
            throw new Http.WebException(429, "rate-limit");
        }
        String url = plugin.settings().web().publicUrl();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", p.shown());
        result.put("poll", p.poll());
        result.put("expires", p.expires());
        result.put("url", url.isEmpty() ? "" : url + "/link?code=" + p.code());
        http.json(200, result);
    }

    private void poll(Http http) throws Exception {
        requireOn();
        if (!server.allow("deck-poll:" + http.ip(), 60)) {
            throw new Http.WebException(429, "rate-limit");
        }
        String id = http.string("poll");
        DeckDevices.Poll p = devices().poll(id == null ? "" : id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", p.state());
        if (p.token() != null) {
            result.put("token", p.token());
            result.put("player", p.playerName());
        }
        http.json(200, result);
    }

    /** What the browser shows before confirming: the name the Stream Deck gave and where it asked from. */
    private void info(Http http) throws Exception {
        requireOn();
        requireDeck(http);
        if (!server.allow("deck-confirm:" + http.account().id(), 20)) {
            throw new Http.WebException(429, "rate-limit");
        }
        DeckDevices.Pending p = devices().byCode(http.query().get("code"));
        if (p == null) {
            throw new Http.WebException(404, "deck-code-invalid");
        }
        http.json(200, Map.of("code", p.shown(), "name", p.name(), "ip", p.ip(), "created", p.created()));
    }

    private void confirm(Http http) throws Exception {
        requireOn();
        requireDeck(http);
        if (!server.allow("deck-confirm:" + http.account().id(), 20)) {
            throw new Http.WebException(429, "rate-limit");
        }
        DeckDevices.Device d = devices().confirm(http.string("code"), http.account().id(), http.account().name());
        if (d == null) {
            throw new Http.WebException(404, "deck-code-invalid");
        }
        audit(http.account().id(), http.account().name(), http.storedIp(), "deck-linked", d.name() + " (web)");
        plugin.getLogger().info(http.account().name() + " connected a Stream Deck (" + d.name() + ") in the browser.");
        http.json(200, Map.of("name", d.name()));
    }

    private void devices(Http http) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        for (DeckDevices.Device d : devices().list(http.account().id())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.id());
            m.put("name", d.name());
            m.put("created", d.created());
            m.put("lastSeen", d.lastSeen());
            m.put("ip", d.ip());
            m.put("via", d.via());
            list.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", plugin.settings().deck().enabled());
        result.put("allowed", may(http.account().id(), Perms.DECK));
        result.put("devices", list);
        http.json(200, result);
    }

    private void revoke(Http http) throws Exception {
        if (!devices().revoke(http.account().id(), http.param("id"))) {
            throw new Http.WebException(404, "not-found");
        }
        audit(http.account().id(), http.account().name(), http.storedIp(), "deck-revoked", http.param("id"));
    }

    private void key(Http http) throws Exception {
        requireOn();
        requireDeck(http);
        if (!server.allow("deck-key:" + http.account().id(), 10)) {
            throw new Http.WebException(429, "rate-limit");
        }
        String name = http.string("name");
        String token = devices().createKey(http.account().id(), http.account().name(), name, http.storedIp());
        audit(http.account().id(), http.account().name(), http.storedIp(), "deck-key", name == null ? "" : name);
        plugin.getLogger().info(http.account().name() + " made a key for a Stream Deck in the browser.");
        http.json(200, Map.of("key", token));
    }

    private void requireDeck(Http http) throws Http.WebException {
        if (!may(http.account().id(), Perms.DECK)) {
            throw new Http.WebException(403, "no-deck-permission");
        }
    }

    // ------------------------------------------------------------- the deck

    private void logout(Http http) throws Exception {
        devices().revoke(http.player().player(), http.player().id());
        staffSeen.remove(http.player().id());
    }

    private void state(Http http) throws Exception {
        UUID player = http.player().player();
        if (self.banned(player, http.ip())) {
            throw new Http.WebException(403, "player-banned");
        }
        String lang = "de".equals(http.query().get("lang")) ? "de" : "en";
        String cacheKey = http.player().id() + ":" + lang;
        Object[] cached = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - (long) cached[0] < CACHE_MILLIS) {
            http.json(200, cached[1]);
            return;
        }
        Map<String, Object> state = build(http, player, lang);
        cache.put(cacheKey, new Object[] {now, state});
        if (cache.size() > 1000) {
            cache.clear();
        }
        http.json(200, state);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> build(Http http, UUID player, String lang) throws Exception {
        String name = http.player().name();
        Map<String, Object> snapshot = server.snapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("time", System.currentTimeMillis());
        result.put("url", plugin.settings().web().publicUrl());
        result.put("serverName", plugin.branding().name());
        boolean team = team(player);
        boolean playerPage = plugin.settings().playerWeb().enabled() && may(player, Perms.WEB_SELF);

        // Who: online right now (admin mode needs it), in which world (sleep).
        Map<String, Object> me = new LinkedHashMap<>();
        Object[] where = server.sync(() -> {
            Player p = Bukkit.getPlayer(player);
            return p == null ? new Object[] {false, null, false}
                    : new Object[] {true, p.getWorld().getName(), plugin.adminMode().active(p)};
        });
        me.put("name", name);
        me.put("uuid", player.toString());
        me.put("online", where[0]);
        me.put("team", team);
        result.put("me", me);

        // The server for everyone: players online (like the start page), restart and maintenance (also in game).
        if (snapshot.get("server") instanceof Map<?, ?> s) {
            Map<String, Object> srv = new LinkedHashMap<>();
            srv.put("online", s.get("online"));
            srv.put("max", s.get("max"));
            result.put("server", srv);
        }
        var next = plugin.modules().nextRestart();
        if (next != null) {
            result.put("restart", Map.of("at", next.at(), "restart", next.restart(), "countdown", next.countdown(),
                    "planned", next.planned(), "reason", next.reason()));
        }
        if (snapshot.get("maintenance") instanceof Map<?, ?> m) {
            result.put("maintenance", m);
        }

        // The own things of the player area.
        if (playerPage) {
            result.put("inbox", self.inboxSummary(player, lang));
            if (self.statsAvailable()) {
                result.put("stats", self.allStats(player, lang));
            }
        }
        if (plugin.feature(Feature.TICKETS) && may(player, Perms.TICKETS_CREATE)) {
            result.put("myTickets", server.sync(() -> {
                int open = 0;
                int unread = 0;
                int days = plugin.settings().tickets().reopenDays();
                for (Ticket t : plugin.tickets().byCreator(player)) {
                    if (!t.visibleInGame()) {
                        continue;
                    }
                    if (t.open() || t.reopenable(days)) {
                        open++;
                    }
                    if (t.creatorUnread()) {
                        unread++;
                    }
                }
                return Map.of("open", open, "unread", unread);
            }));
        }
        if (snapshot.get("mrsleep") instanceof Map<?, ?> sleep && sleep.get("worlds") instanceof List<?> worlds) {
            Map<?, ?> chosen = null;
            for (Object o : worlds) {
                Map<?, ?> w = (Map<?, ?>) o;
                if (Boolean.TRUE.equals(w.get("managed")) && (chosen == null || w.get("name").equals(where[1]))) {
                    chosen = w;
                }
            }
            if (chosen != null) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("world", chosen.get("name"));
                s.put("night", chosen.get("night"));
                s.put("sleeping", chosen.get("sleeping"));
                s.put("needed", chosen.get("needed"));
                result.put("sleep", s);
            }
        }

        // Keys of the plugins (chunk loaders, duels ...) - each module decides what the viewer may see.
        List<Map<String, Object>> tiles = new ArrayList<>();
        for (var entry : plugin.modules().webModules().entrySet()) {
            AdminModule module = plugin.modules().module(entry.getKey());
            SelfViewer viewer = self.viewer(http, entry.getKey());
            viewer = new SelfViewer(viewer.player(), viewer.name(), lang, viewer.permissions());
            SelfViewer v = viewer;
            List<DeckTile> list = server.sync(() -> plugin.modules().call(module,
                    () -> entry.getValue().deckTiles(v), List.<DeckTile>of()));
            for (DeckTile t : list) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("module", entry.getKey());
                m.put("id", t.id());
                m.put("kind", t.kind());
                m.put("title", t.title());
                m.put("value", t.value());
                m.put("detail", t.detail());
                m.put("tone", t.tone());
                m.put("link", t.link());
                m.put("pressable", t.pressable());
                m.put("confirm", t.confirm());
                tiles.add(m);
            }
        }
        result.put("tiles", tiles);

        if (team) {
            result.put("team", team(http, player, (Boolean) where[0], (Boolean) where[2]));
        }
        return result;
    }

    /** What the team keys show: the dashboard of the web interface plus the few things only the Stream Deck has. */
    private Map<String, Object> team(Http http, UUID player, boolean online, boolean adminMode) throws Exception {
        Map<String, Object> team = new LinkedHashMap<>(PanelRoutes.overview(server, player));
        // The dashboard's live list of players and the plugin tiles' facts are not needed on a key.
        if (team.get("server") instanceof Map<?, ?> s) {
            Map<String, Object> srv = new LinkedHashMap<>();
            s.forEach((k, v) -> {
                if (!"players".equals(k)) {
                    srv.put(String.valueOf(k), v);
                }
            });
            team.put("server", srv);
        }
        if (plugin.feature(Feature.SERVER_STATUS) && may(player, Perms.STATUS_HEALTH)) {
            List<Double> tps = new ArrayList<>();
            List<Double> mspt = new ArrayList<>();
            for (var point : plugin.health().range(10 * 60_000L, 30)) {
                tps.add(Math.round(point.tps() * 10) / 10.0);
                mspt.add(Math.round(point.mspt() * 10) / 10.0);
            }
            team.put("history", Map.of("tps", tps, "mspt", mspt));
        }
        if (plugin.feature(Feature.STAFF_CHAT) && may(player, Perms.STAFFCHAT)) {
            long seen = staffSeen.getOrDefault(http.player().id(), 0L);
            var lines = plugin.staffChat().recent();
            long unread = lines.stream().filter(l -> l.time() > seen).count();
            Map<String, Object> chat = new LinkedHashMap<>();
            chat.put("unread", unread);
            if (!lines.isEmpty()) {
                var last = lines.getLast();
                chat.put("last", Map.of("from", last.from(), "text", last.text(), "time", last.time()));
            }
            team.put("staffChat", chat);
        }
        if (plugin.feature(Feature.ADMIN_MODE) && may(player, AdminModeManager.PERM_USE)) {
            team.put("adminMode", Map.of("online", online, "active", adminMode));
        }
        if (may(player, Perms.SERVER_RESTART)) {
            Map<String, Object> control = new LinkedHashMap<>();
            control.put("enabled", plugin.restarts().enabled());
            control.put("delays", plugin.settings().versions().restartDelays());
            control.put("running", server.sync(() -> plugin.restarts().endsAt()));
            control.put("roomRestarting", plugin.waitingRoom().roomRestarting());
            team.put("restartControl", control);
        }
        if (plugin.feature(Feature.ANNOUNCE) && may(player, Perms.ANNOUNCE)) {
            team.put("announce", server.sync(() -> {
                List<Map<String, Object>> list = new ArrayList<>();
                var announcer = plugin.announcer();
                for (String p : announcer.menuPresets()) {
                    list.add(Map.of("name", p, "values", announcer.needsValues(p)));
                }
                return list;
            }));
        }
        return team;
    }

    /** A key with an effect. Body: {action, ...}; answers {ok} or 400 {error, message}. */
    private void press(Http http) throws Exception {
        UUID player = http.player().player();
        String name = http.player().name();
        if (!server.allow("deck-press:" + http.player().id(), 30)) {
            throw new Http.WebException(429, "rate-limit");
        }
        if (self.banned(player, http.ip())) {
            throw new Http.WebException(403, "player-banned");
        }
        String action = String.valueOf(http.string("action"));
        String by = name + " (Stream Deck)";
        cache.keySet().removeIf(k -> k.startsWith(http.player().id() + ":"));
        switch (action) {
            case "module" -> {
                String moduleId = String.valueOf(http.string("module"));
                String tile = String.valueOf(http.string("tile"));
                WebModule web = plugin.modules().web(moduleId);
                AdminModule module = plugin.modules().module(moduleId);
                if (web == null || module == null) {
                    throw new Http.WebException(404, "not-found");
                }
                SelfViewer viewer = self.viewer(http, moduleId);
                String refused = server.sync(() -> plugin.modules().attempt(module, () -> web.deckPress(viewer, tile)));
                if (refused != null) {
                    http.json(400, Map.of("error", "module", "message", refused));
                    return;
                }
                plugin.getLogger().info(by + " " + moduleId + ": " + tile + ".");
            }
            case "staffchat-seen" -> {
                var lines = plugin.staffChat().recent();
                staffSeen.put(http.player().id(), lines.isEmpty() ? System.currentTimeMillis() : lines.getLast().time());
            }
            case "admin-mode" -> {
                requireTeam(player, AdminModeManager.PERM_USE);
                if (!plugin.feature(Feature.ADMIN_MODE)) {
                    throw new Http.WebException(404, "feature-disabled");
                }
                String error = server.sync(() -> {
                    Player p = Bukkit.getPlayer(player);
                    if (p == null) {
                        return "deck-offline";
                    }
                    if (plugin.adminMode().active(p)) {
                        plugin.adminMode().disable(p, false);
                    } else {
                        plugin.adminMode().enable(p);
                    }
                    return null;
                });
                if (error != null) {
                    throw new Http.WebException(409, error);
                }
                audit(player, name, http.storedIp(), "deck-admin-mode", "");
            }
            case "restart" -> {
                requireTeam(player, Perms.SERVER_RESTART);
                if (!plugin.restarts().enabled()) {
                    throw new Http.WebException(400, "restart-disabled");
                }
                long delay;
                try {
                    delay = Long.parseLong(String.valueOf(http.string("delay")).trim());
                } catch (NumberFormatException e) {
                    throw new Http.WebException(400, "invalid-duration");
                }
                // Only the normal choices - an emergency restart stays in the browser (with a fresh sign-in).
                if (!plugin.settings().versions().restartDelays().contains(delay)) {
                    throw new Http.WebException(400, "invalid-duration");
                }
                if (plugin.waitingRoom().roomRestarting()) {
                    throw new Http.WebException(409, "waitroom-restarting");
                }
                String reason = http.string("reason");
                if (!server.sync(() -> plugin.restarts().start(delay, by, true,
                        reason == null || reason.isBlank() ? null : reason.strip()))) {
                    throw new Http.WebException(409, "restart-running");
                }
                audit(player, name, http.storedIp(), "server-restart", delay / 1000 + " s (Stream Deck)");
            }
            case "restart-cancel" -> {
                requireTeam(player, Perms.SERVER_RESTART);
                if (!server.sync(() -> plugin.restarts().cancel(by))) {
                    throw new Http.WebException(409, "restart-none");
                }
                audit(player, name, http.storedIp(), "server-restart-cancelled", "Stream Deck");
            }
            case "announce" -> {
                requireTeam(player, Perms.ANNOUNCE);
                if (!plugin.feature(Feature.ANNOUNCE)) {
                    throw new Http.WebException(404, "feature-disabled");
                }
                String preset = String.valueOf(http.string("preset"));
                String error = server.sync(() -> {
                    var announcer = plugin.announcer();
                    // Only the own templates (like the web interface), and only those without values to fill in.
                    if (!announcer.menuPresets().contains(preset)) {
                        return "not-found";
                    }
                    if (announcer.needsValues(preset)) {
                        return "deck-announce-values";
                    }
                    announcer.announcePreset(preset, List.of(), by, announcer.postsToDiscord(preset));
                    return null;
                });
                if (error != null) {
                    throw new Http.WebException(error.equals("not-found") ? 404 : 400, error);
                }
                audit(player, name, http.storedIp(), "announce", preset + " (Stream Deck)");
            }
            default -> throw new Http.WebException(400, "unknown-action");
        }
        http.json(200, Map.of("ok", true));
    }

    private void requireTeam(UUID player, String permission) throws Http.WebException {
        if (!team(player) || !may(player, permission)) {
            throw new Http.WebException(403, "no-permission");
        }
    }

    private void audit(UUID player, String name, String ip, String action, String detail) {
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), player, name, ip, action, detail));
    }
}

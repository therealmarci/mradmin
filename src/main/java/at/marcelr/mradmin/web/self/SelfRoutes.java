package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.api.AdminModule;
import at.marcelr.mradmin.api.SelfArea;
import at.marcelr.mradmin.api.SelfViewer;
import at.marcelr.mradmin.api.WebModule;
import at.marcelr.mradmin.hook.Bans;
import at.marcelr.mradmin.web.Http;
import at.marcelr.mradmin.web.WebServer;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The player area of the web interface ("/me" in the browser, API under /api/self) - every player sees and manages only their own things.
 * <pre>
 *  POST /api/self/login         {code}   redeems the 6-digit code from /web, signs this browser in (open)
 *  GET  /api/self                        who is signed in and which areas there are
 *  POST /api/self/logout                 signs this browser out
 *  GET  /api/self/devices                the player's signed-in browsers
 *  POST /api/self/devices/{id}/revoke    signs one of them out
 *  GET  /api/self/areas/{module}/{area}?lang=de          one area of a plugin (built from SelfBlocks)
 *  POST /api/self/areas/{module}/{area}/action {action, args, lang}   a button of it -> 400 {message} on refusal
 *  (tickets: {@link SelfTickets}, rankings: {@link SelfStats}, news slideshow: {@link SelfNews})
 * </pre>
 * Every request checks again that the player still has mradmin.web.self and is not banned (LibertyBans).
 */
public final class SelfRoutes {

    private static final long BAN_CACHE_MILLIS = 60_000L;

    private final WebServer server;
    private final MRAdmin plugin;
    /** player -> [banned 1/0, checked until] */
    private final Map<UUID, long[]> banCache = new ConcurrentHashMap<>();
    private SelfTickets tickets;
    private SelfStats stats;
    private SelfNews news;

    private SelfRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        SelfRoutes r = new SelfRoutes(server);
        server.open("POST", "/api/self/login", r::login);
        server.self("GET", "/api/self", r::me);
        server.self("POST", "/api/self/logout", r::logout);
        server.self("GET", "/api/self/devices", r::devices);
        server.self("POST", "/api/self/devices/{id}/revoke", r::revoke);
        server.self("GET", "/api/self/areas/{module}/{area}", r::area);
        server.self("POST", "/api/self/areas/{module}/{area}/action", r::action);
        r.tickets = new SelfTickets(server, r);
        r.tickets.register();
        r.stats = new SelfStats(server, r);
        r.stats.register();
        r.news = new SelfNews(server, r, r.tickets);
        r.news.register();
    }

    private PlayerSessions sessions() {
        return server.playerSessions();
    }

    // ------------------------------------------------------------- sign-in

    private void login(Http http) throws Exception {
        if (!plugin.settings().playerWeb().enabled()) {
            throw new Http.WebException(404, "player-web-off");
        }
        // 6 digits: few tries per address (and PlayerSessions limits wrong codes from everyone together).
        if (!server.allow("me-login:" + http.ip(), 6)) {
            throw new Http.WebException(429, "rate-limit");
        }
        PlayerSessions.Result result = sessions().redeem(http.string("code"), http.storedIp(), http.header("User-Agent"));
        if (result.error() != null) {
            throw new Http.WebException(401, result.error());
        }
        UUID player = result.session().player();
        if (!server.permissions().has(player, Perms.WEB_SELF)) {
            sessions().revokeAll(player);
            throw new Http.WebException(403, "no-self-permission");
        }
        if (banned(player, http.ip())) {
            sessions().logout(result.token());
            throw new Http.WebException(403, "player-banned");
        }
        http.setCookie(WebServer.PLAYER_COOKIE, result.token(),
                sessions().cookieSeconds(), plugin.settings().web().secure());
        plugin.getLogger().info(result.session().name() + " signed in to their own page in the web interface.");
        http.json(200, Map.of("name", result.session().name(), "uuid", player.toString()));
    }

    /**
     * Still allowed: the permission (otherwise every browser of the player is signed out) and no ban. Every route of
     * the player area calls this first - also those of the modules later.
     */
    public void check(Http http) throws Exception {
        UUID player = http.player().player();
        if (!server.permissions().has(player, Perms.WEB_SELF)) {
            sessions().revokeAll(player);
            throw new Http.WebException(403, "no-self-permission");
        }
        if (banned(player, http.ip())) {
            throw new Http.WebException(403, "player-banned");
        }
    }

    /** Banned through LibertyBans (also by IP)? Asked at most once a minute per player; without LibertyBans never. */
    private boolean banned(UUID player, String ip) {
        long now = System.currentTimeMillis();
        long[] cached = banCache.get(player);
        if (cached != null && cached[1] > now) {
            return cached[0] == 1;
        }
        boolean banned = false;
        Bans bans = Bans.get(plugin);
        if (bans != null) {
            try {
                banned = bans.applicableBan(player, InetAddress.getByName(ip)) != null;
            } catch (Exception e) {
                plugin.getLogger().warning("Could not check the ban of " + player + " for the player area: " + e.getMessage());
            }
        }
        banCache.put(player, new long[] {banned ? 1 : 0, now + BAN_CACHE_MILLIS});
        return banned;
    }

    // ---------------------------------------------------------------- pages

    private void me(Http http) throws Exception {
        check(http);
        PlayerSessions.Session s = http.player();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", s.player().toString());
        result.put("name", s.name());
        // The areas the installed plugins offer (chunk loaders, duels ...): title, icon and the start page tile.
        List<Map<String, Object>> areas = new ArrayList<>();
        for (var entry : plugin.modules().webModules().entrySet()) {
            SelfViewer viewer = viewer(http, entry.getKey());
            AdminModule module = plugin.modules().module(entry.getKey());
            List<SelfArea> list = server.sync(() -> plugin.modules().call(module,
                    () -> entry.getValue().selfAreas(viewer), List.<SelfArea>of()));
            for (SelfArea area : list) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("module", entry.getKey());
                a.put("id", area.id());
                a.put("title", area.title());
                a.put("icon", area.icon());
                a.put("summary", SelfJson.blocks(area.summary()));
                areas.add(a);
            }
        }
        result.put("areas", areas);
        // Own tickets (MRadmin itself, see SelfTickets) - like /report in game.
        result.put("tickets", tickets.available(s.player()));
        result.put("stats", stats.available());
        result.put("news", news.available());
        String lang = "de".equals(http.query().get("lang")) ? "de" : "en";
        result.put("personal", stats.personal(s.player(), lang));
        http.json(200, result);
    }

    /** One area of a module with all its blocks. */
    private void area(Http http) throws Exception {
        check(http);
        WebModule web = web(http);
        SelfViewer viewer = viewer(http, http.param("module"));
        AdminModule module = plugin.modules().module(http.param("module"));
        String id = http.param("area");
        SelfArea area = server.sync(() -> plugin.modules().call(module, () -> web.selfAreas(viewer), List.<SelfArea>of()))
                .stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
        if (area == null) {
            throw new Http.WebException(404, "not-found");
        }
        http.json(200, Map.of("module", module.id(), "id", area.id(), "title", area.title(), "icon", area.icon(),
                "summary", SelfJson.blocks(area.summary()), "blocks", SelfJson.blocks(area.blocks())));
    }

    /** A button of an area: the module checks and does it, or answers with the reason (in the page's language). */
    private void action(Http http) throws Exception {
        check(http);
        if (!server.allow("self-action:" + http.player().player(), 60)) {
            throw new Http.WebException(429, "rate-limit");
        }
        WebModule web = web(http);
        SelfViewer viewer = viewer(http, http.param("module"));
        AdminModule module = plugin.modules().module(http.param("module"));
        String action = http.string("action");
        Map<String, String> args = new LinkedHashMap<>();
        if (http.body().get("args") instanceof com.google.gson.JsonObject o) {
            o.entrySet().forEach(e -> {
                if (e.getValue().isJsonPrimitive() && args.size() < 20) {
                    String v = e.getValue().getAsString();
                    args.put(e.getKey(), v.length() > 200 ? v.substring(0, 200) : v);
                }
            });
        }
        if (action == null || action.isBlank()) {
            throw new Http.WebException(400, "unknown-action");
        }
        String area = http.param("area");
        String refused = server.sync(() -> plugin.modules().attempt(module, () -> web.selfAction(viewer, area, action, args)));
        if (refused != null) {
            http.json(400, Map.of("error", "module", "message", refused));
            return;
        }
        plugin.getLogger().info(viewer.name() + " (own page) " + module.id() + "/" + area + ": " + action + ".");
    }

    private WebModule web(Http http) throws Http.WebException {
        WebModule web = plugin.modules().web(http.param("module"));
        if (web == null) {
            throw new Http.WebException(404, "not-found");
        }
        return web;
    }

    /** Who looks, in which language ("lang" of the query or body), with the module's permissions resolved here. */
    SelfViewer viewer(Http http, String moduleId) throws java.io.IOException {
        PlayerSessions.Session s = http.player();
        String lang = http.query().getOrDefault("lang", http.method().equals("POST") ? http.string("lang") : null);
        AdminModule module = plugin.modules().module(moduleId);
        WebModule web = plugin.modules().web(moduleId);
        java.util.Set<String> granted = new java.util.HashSet<>();
        if (module != null && web != null) {
            for (String permission : plugin.modules().call(module, web::selfPermissions, java.util.Set.<String>of())) {
                if (server.permissions().has(s.player(), permission)) {
                    granted.add(permission);
                }
            }
        }
        return new SelfViewer(s.player(), s.name(), "de".equals(lang) ? "de" : "en", java.util.Set.copyOf(granted));
    }

    private void logout(Http http) throws Exception {
        sessions().logout(http.cookie(WebServer.PLAYER_COOKIE));
        http.setCookie(WebServer.PLAYER_COOKIE, "", 0, plugin.settings().web().secure());
    }

    private void devices(Http http) throws Exception {
        check(http);
        List<Map<String, Object>> list = new ArrayList<>();
        for (PlayerSessions.Session s : sessions().devices(http.player().player())) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", s.id());
            d.put("agent", s.agent());
            d.put("created", s.created());
            d.put("lastSeen", s.lastSeen());
            d.put("current", s.id().equals(http.player().id()));
            list.add(d);
        }
        http.json(200, list);
    }

    private void revoke(Http http) throws Exception {
        check(http);
        if (!sessions().revoke(http.player().player(), http.param("id"))) {
            throw new Http.WebException(404, "not-found");
        }
    }
}

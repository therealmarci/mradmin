package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
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
 *  POST /api/self/login         {code}   redeems the code from /web, signs this browser in (open)
 *  GET  /api/self                        who is signed in and which areas there are
 *  POST /api/self/logout                 signs this browser out
 *  GET  /api/self/devices                the player's signed-in browsers
 *  POST /api/self/devices/{id}/revoke    signs one of them out
 * </pre>
 * Every request checks again that the player still has mradmin.web.self and is not banned (LibertyBans).
 */
public final class SelfRoutes {

    private static final long BAN_CACHE_MILLIS = 60_000L;

    private final WebServer server;
    private final MRAdmin plugin;
    /** player -> [banned 1/0, checked until] */
    private final Map<UUID, long[]> banCache = new ConcurrentHashMap<>();

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
    }

    private PlayerSessions sessions() {
        return server.playerSessions();
    }

    // ------------------------------------------------------------- sign-in

    private void login(Http http) throws Exception {
        if (!plugin.settings().playerWeb().enabled()) {
            throw new Http.WebException(404, "player-web-off");
        }
        if (!server.allow("me-login:" + http.ip(), 20)) {
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
                plugin.settings().playerWeb().sessionDays() * 86_400L, plugin.settings().web().secure());
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
        // The areas the installed plugins offer (chunk loaders, duels ...) follow with the module API.
        result.put("areas", List.of());
        http.json(200, result);
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

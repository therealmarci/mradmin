package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.map.MapService;
import at.marcelr.mradmin.storage.Storage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The team's switches for the map of the player page (mradmin.map.admin): the whole map off for everyone, and the
 * players on it off for everyone - separately, right away, kept over restarts.
 * <pre>
 *  GET  /api/map/locks                       {page: {off, by, since}, players: {...}}
 *  POST /api/map/locks {which, off}          which = page | players
 * </pre>
 */
public final class MapRoutes {

    private final WebServer server;
    private final MRAdmin plugin;

    private MapRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        MapRoutes r = new MapRoutes(server);
        server.route("GET", "/api/map/locks", r::locks);
        server.route("POST", "/api/map/locks", r::set);
    }

    private MapService map(Http http) throws Http.WebException {
        if (plugin.map() == null) {
            throw new Http.WebException(404, "feature-disabled");
        }
        if (!server.permissions().has(http.account().id(), Perms.MAP_ADMIN)) {
            throw new Http.WebException(403, "no-permission");
        }
        return plugin.map();
    }

    private void locks(Http http) throws Exception {
        MapService map = map(http);
        http.json(200, Map.of("page", json(map.pageLock()), "players", json(map.playersLock())));
    }

    private void set(Http http) throws Exception {
        MapService map = map(http);
        String which = http.string("which");
        if (!"page".equals(which) && !"players".equals(which)) {
            throw new Http.WebException(400, "bad-request");
        }
        boolean off = "true".equals(http.string("off"));
        String by = http.account().name();
        server.sync(() -> {
            map.setLock(which.equals("players"), off, by);
            return null;
        });
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), http.account().id(), by,
                http.storedIp(), "map-" + which + (off ? "-off" : "-on"), ""));
        locks(http);
    }

    private static Map<String, Object> json(MapService.Lock lock) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("off", lock.off());
        m.put("by", lock.by());
        m.put("since", lock.since());
        return m;
    }
}

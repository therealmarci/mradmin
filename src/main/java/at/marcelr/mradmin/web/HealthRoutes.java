package at.marcelr.mradmin.web;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.health.HotChunks;
import org.bukkit.Bukkit;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The health dashboard (mradmin.status.health).
 * <pre>
 *  GET /api/health?range=1m|1h|6h|24h|7d|30d   history (at most 360 points)
 *  GET /api/health/latest                  the newest point (every second)
 *  GET /api/health/system                   uptime, Java, CPU cores, disk, threads, worlds, latest point
 *  GET /api/health/lag                      chunks with the most entities (counted at most every 30 s)
 * </pre>
 */
public final class HealthRoutes {

    private static final Map<String, Long> RANGES = Map.of("1m", 60_000L, "1h", 3_600_000L, "6h", 6 * 3_600_000L,
            "24h", 24 * 3_600_000L, "7d", 7 * 86_400_000L, "30d", 30 * 86_400_000L);
    private static final int MAX_POINTS = 360;
    private static final long LAG_CACHE_MILLIS = 30_000L;

    private final WebServer server;
    private final MRAdmin plugin;
    private volatile List<Map<String, Object>> lag = List.of();
    private volatile long lagTime;

    private HealthRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        HealthRoutes r = new HealthRoutes(server);
        server.route("GET", "/api/health", r::history);
        server.route("GET", "/api/health/system", r::system);
        server.route("GET", "/api/health/latest", r::latest);
        server.route("GET", "/api/health/lag", r::lag);
    }

    private void require(Http http) throws Http.WebException {
        if (!plugin.feature(Feature.SERVER_STATUS)) {
            throw new Http.WebException(404, "feature-disabled");
        }
        if (!server.permissions().has(http.account().id(), Perms.STATUS_HEALTH)) {
            throw new Http.WebException(403, "no-permission");
        }
    }

    private void history(Http http) throws Exception {
        require(http);
        String query = http.exchange().getRequestURI().getQuery();
        String range = query != null && query.startsWith("range=") ? query.substring(6) : "1h";
        long millis = RANGES.getOrDefault(range, RANGES.get("1h"));
        // Never more than is kept (health.history).
        millis = Math.min(millis, Math.max(24 * 3_600_000L, plugin.settings().health().history()));
        http.json(200, Map.of("range", range, "history", plugin.settings().health().history(),
                "points", plugin.health().range(millis, MAX_POINTS)));
    }

    /** The newest point (every second) - the key figures stay live even when a long range is shown. */
    private void latest(Http http) throws Exception {
        require(http);
        var latest = plugin.health().latest();
        http.json(200, latest == null ? Map.of() : latest);
    }

    private void system(Http http) throws Exception {
        require(http);
        Map<String, Object> info = new LinkedHashMap<>();
        var runtime = ManagementFactory.getRuntimeMXBean();
        info.put("uptime", runtime.getUptime());
        info.put("java", System.getProperty("java.vendor") + " " + System.getProperty("java.version"));
        info.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        info.put("cores", Runtime.getRuntime().availableProcessors());
        info.put("threads", ManagementFactory.getThreadMXBean().getThreadCount());
        info.put("server", Bukkit.getName() + " " + Bukkit.getMinecraftVersion());
        info.put("plugins", Bukkit.getPluginManager().getPlugins().length);
        File root = new File(".").getAbsoluteFile();
        info.put("diskFree", root.getUsableSpace() / 1_048_576);
        info.put("diskTotal", root.getTotalSpace() / 1_048_576);
        info.put("worlds", plugin.health().worlds());
        info.put("latest", plugin.health().latest());
        http.json(200, info);
    }

    /** The chunk count goes through every entity - only when someone looks, and at most every 30 seconds. */
    private void lag(Http http) throws Exception {
        require(http);
        if (System.currentTimeMillis() - lagTime > LAG_CACHE_MILLIS) {
            lag = server.sync(() -> {
                List<Map<String, Object>> list = new ArrayList<>();
                for (HotChunks.Chunk c : HotChunks.count(21)) {
                    List<Map<String, Object>> types = new ArrayList<>();
                    c.types().entrySet().stream().sorted(Map.Entry.<org.bukkit.entity.EntityType, Integer>comparingByValue().reversed())
                            .limit(4).forEach(e -> types.add(Map.of("type", e.getKey().name().toLowerCase(Locale.ROOT), "count", e.getValue())));
                    list.add(Map.of("world", c.world().getName(), "x", c.x() * 16 + 8, "z", c.z() * 16 + 8,
                            "count", c.count(), "types", types));
                }
                return list;
            });
            lagTime = System.currentTimeMillis();
        }
        http.json(200, Map.of("time", lagTime, "chunks", lag));
    }
}

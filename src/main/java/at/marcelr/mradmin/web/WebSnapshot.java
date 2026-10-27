package at.marcelr.mradmin.web;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.maintenance.MaintenanceManager;
import at.marcelr.mradmin.maintenance.Window;
import at.marcelr.mradmin.player.Npcs;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The server state for the web interface, taken once per second on the server thread: only values the server has
 * anyway (no counting of entities or chunks). The web reads this copy - however many pages are open, the server does
 * the same work.
 */
final class WebSnapshot {

    private WebSnapshot() {
    }

    static Map<String, Object> build(MRAdmin plugin) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("time", System.currentTimeMillis());

        double[] tps = Bukkit.getTPS();
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("tps", List.of(round(Math.min(20, tps[0])), round(Math.min(20, tps[1])), round(Math.min(20, tps[2]))));
        server.put("mspt", round(Bukkit.getAverageTickTime()));
        server.put("online", Npcs.online().size());
        server.put("max", Bukkit.getMaxPlayers());
        List<Map<String, Object>> players = new ArrayList<>();
        Npcs.online().stream().sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(p -> players.add(Map.of("name", p.getName(), "uuid", p.getUniqueId().toString(),
                        "world", p.getWorld().getName(), "ping", p.getPing(),
                        "admin", plugin.adminMode().active(p), "frozen", plugin.freeze().frozen(p.getUniqueId()))));
        server.put("players", players);
        Runtime rt = Runtime.getRuntime();
        server.put("ramUsed", (rt.totalMemory() - rt.freeMemory()) / 1_048_576);
        server.put("ramMax", rt.maxMemory() / 1_048_576);
        server.put("uptime", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
        java.io.File root = new java.io.File(".").getAbsoluteFile();
        server.put("diskFree", root.getUsableSpace() / 1_048_576);
        server.put("diskTotal", root.getTotalSpace() / 1_048_576);
        result.put("server", server);

        // MRchunks (optional): counts and server pause - cheap, the plugin keeps them anyway.
        if (overview(plugin, "mrchunks") instanceof Map<?, ?> overview) {
            result.put("mrchunks", overview);
        }
        // MRsleep (optional): sleep status per world and /nosleep blocks.
        if (overview(plugin, "mrsleep") instanceof Map<?, ?> overview) {
            result.put("mrsleep", overview);
        }
        // Hints of all modules for the start page (filtered by permission per member, see PanelRoutes).
        Map<String, Object> moduleAlerts = new LinkedHashMap<>();
        plugin.modules().webModules().forEach((id, web) -> {
            List<Map<String, Object>> list = new ArrayList<>();
            for (var a : plugin.modules().call(plugin.modules().module(id), web::alerts, List.<at.marcelr.mradmin.api.WebAlert>of())) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("tone", a.tone());
                entry.put("text", a.text());
                entry.put("page", a.page());
                list.add(entry);
            }
            if (!list.isEmpty()) {
                moduleAlerts.put(id, list);
            }
        });
        result.put("moduleAlerts", moduleAlerts);

        if (plugin.feature(Feature.MAINTENANCE)) {
            MaintenanceManager m = plugin.maintenance();
            Map<String, Object> maintenance = new LinkedHashMap<>();
            maintenance.put("phase", m.phase().name().toLowerCase(Locale.ROOT));
            Window w = m.current();
            if (w != null) {
                maintenance.put("title", w.title());
                maintenance.put("start", w.start() == null ? 0 : w.start().toEpochMilli());
                maintenance.put("end", w.end() == null ? 0 : w.end().toEpochMilli());
                maintenance.put("source", w.source());
            }
            result.put("maintenance", maintenance);
        }
        if (plugin.feature(Feature.TICKETS)) {
            Map<String, Integer> claimed = new java.util.HashMap<>();
            plugin.tickets().openClaimedCounts().forEach((uuid, n) -> claimed.put(uuid.toString(), n));
            result.put("tickets", Map.of("open", plugin.tickets().openCount(), "unread", plugin.tickets().staffUnreadCount(),
                    "claimed", claimed));
        }
        return Map.copyOf(result);
    }

    /** The "overview" data of a module (counts it keeps anyway - cheap), or null without it. */
    private static Object overview(MRAdmin plugin, String id) {
        var web = plugin.modules().web(id);
        return web == null ? null
                : plugin.modules().call(plugin.modules().module(id), () -> web.data("overview", Map.of()), null);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}

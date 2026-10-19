package at.marcelr.mradmin.web;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.maintenance.MaintenanceManager;
import at.marcelr.mradmin.maintenance.Window;
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
        server.put("online", Bukkit.getOnlinePlayers().size());
        server.put("max", Bukkit.getMaxPlayers());
        List<Map<String, Object>> players = new ArrayList<>();
        Bukkit.getOnlinePlayers().stream().sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(p -> players.add(Map.of("name", p.getName(), "uuid", p.getUniqueId().toString(),
                        "world", p.getWorld().getName(), "ping", p.getPing(),
                        "admin", plugin.adminMode().active(p), "frozen", plugin.freeze().frozen(p.getUniqueId()))));
        server.put("players", players);
        Runtime rt = Runtime.getRuntime();
        server.put("ramUsed", (rt.totalMemory() - rt.freeMemory()) / 1_048_576);
        server.put("ramMax", rt.maxMemory() / 1_048_576);
        result.put("server", server);

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
            result.put("tickets", Map.of("open", plugin.tickets().openCount(), "unread", plugin.tickets().staffUnreadCount()));
        }
        return Map.copyOf(result);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}

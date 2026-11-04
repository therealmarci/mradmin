package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.log.EventLog;
import at.marcelr.mradmin.storage.Storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The page Log: events of MRadmin and of the modules (EventLog) together with the web log (actions in the web
 * interface, from the storage) - mradmin.web.audit - and the console output (source "console", from Paper's log files,
 * mradmin.web.console.view), newest first. New console lines come from /api/console/tail (memory, not the files).
 * <pre>
 *  GET /api/logs?sources=a,b&q=&since=&before=&limit=   {entries: [{time, source, event, text, color}], sources, counts}
 * </pre>
 * sources (empty = all): "web" = the web log, "mradmin" = MRadmin itself, otherwise the id of a module. before = load
 * older ones. counts=1: entries per source in the range that match the text (the console part reads all files of the
 * range - the page asks only on a new search).
 */
public final class LogRoutes {

    private static final int MAX_LIMIT = 500;
    private static final int WEB_COLOR = 0x3B82F6;
    /** Console lines by level: grey, amber, red. */
    private static final Map<String, Integer> CONSOLE_COLORS = Map.of("info", 0x8B95A5, "warn", 0xF59E0B, "error", 0xE5484D);

    private final WebServer server;
    private final MRAdmin plugin;

    private LogRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        server.route("GET", "/api/logs", new LogRoutes(server)::list);
    }

    private void list(Http http) throws Exception {
        boolean events = server.permissions().has(http.account().id(), Perms.WEB_AUDIT);
        boolean console = ConsoleRoutes.enabled(plugin) && server.permissions().has(http.account().id(), Perms.WEB_CONSOLE_VIEW);
        if (!events && !console) {
            throw new Http.WebException(403, "no-permission");
        }
        Map<String, String> query = http.query();
        Set<String> chosen = new LinkedHashSet<>();
        for (String s : (query.getOrDefault("sources", query.getOrDefault("source", ""))).split(",")) {
            if (!s.isBlank()) {
                chosen.add(s.strip());
            }
        }
        boolean all = chosen.isEmpty();
        boolean counting = "1".equals(query.get("counts"));
        String q = query.get("q");
        long since = number(query.get("since"));
        long before = number(query.get("before"));
        int limit = (int) Math.clamp(number(query.get("limit")) <= 0 ? 200 : number(query.get("limit")), 1, MAX_LIMIT);
        List<EventLog.Entry> entries = new ArrayList<>();
        if (events && (all || chosen.stream().anyMatch(c -> !c.equals("web") && !c.equals("console")))) {
            entries.addAll(plugin.eventLog().find(all ? null : chosen, q, since, before, limit + 1));
        }
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        int webCount = 0;
        List<Storage.AuditRow> audit = plugin.storage().recentAudit(2000);
        for (Storage.AuditRow row : audit) {
            if (row.time() >= since && (needle.isEmpty() || webText(row).toLowerCase(Locale.ROOT).contains(needle))) {
                webCount++;
            }
        }
        if (events && (all || chosen.contains("web"))) {
            for (Storage.AuditRow row : audit) {
                if (row.time() < since || (before > 0 && row.time() >= before)) {
                    continue;
                }
                String text = webText(row);
                if (!needle.isEmpty() && !text.toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                entries.add(new EventLog.Entry(row.time(), "web", row.action(), text, WEB_COLOR));
            }
        }
        int consoleCount = 0;
        if (console && (all || chosen.contains("console") || counting)) {
            java.time.ZoneId zone = java.time.ZoneId.systemDefault();
            ServerLogFiles files = new ServerLogFiles(plugin.getServer().getWorldContainer().toPath().resolve("logs"), zone);
            ServerLogFiles.Result result = files.find(java.time.LocalDate.now(zone), since, before, q, null, limit + 1, counting,
                    plugin.getConfig().getBoolean("web.console.hide-links", true),
                    plugin.getConfig().getBoolean("web.console.hide-ips", true));
            if (all || chosen.contains("console")) {
                // key = time plus a running number within the second - keeps the order and "older than" exact
                for (ServerLogFiles.Line line : result.lines()) {
                    entries.add(new EventLog.Entry(line.key(), "console", line.level(), line.text(), CONSOLE_COLORS.get(line.level())));
                }
            }
            if (result.counts() != null) {
                consoleCount = result.counts().values().stream().mapToInt(Integer::intValue).sum();
            }
        }
        entries.sort(Comparator.comparingLong(EventLog.Entry::time).reversed());
        List<Map<String, Object>> out = new ArrayList<>();
        for (EventLog.Entry e : entries.subList(0, Math.min(limit, entries.size()))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", e.time());
            m.put("source", e.source());
            m.put("name", e.source().equals("web") || e.source().equals("mradmin") || e.source().equals("console") ? e.source()
                    : plugin.modules().displayName(e.source()));
            m.put("event", e.event());
            m.put("text", e.text());
            m.put("color", String.format("#%06X", e.color()));
            out.add(m);
        }
        Set<String> sources = new LinkedHashSet<>();
        if (events) {
            sources.addAll(List.of("mradmin", "web"));
            sources.addAll(plugin.eventLog().sources());
        }
        if (console) {
            sources.add("console");
        }
        Map<String, String> names = new LinkedHashMap<>();
        for (String s : sources) {
            names.put(s, s.equals("web") || s.equals("mradmin") || s.equals("console") ? s : plugin.modules().displayName(s));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entries", out);
        result.put("sources", names);
        result.put("more", entries.size() > limit);
        if (counting) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            if (events) {
                counts.putAll(plugin.eventLog().counts(q, since));
                counts.put("web", webCount);
            }
            if (console) {
                counts.put("console", consoleCount);
            }
            result.put("counts", counts);
        }
        http.json(200, result);
    }

    private static String webText(Storage.AuditRow row) {
        return row.name() + ": " + row.action() + (row.detail() == null || row.detail().isEmpty() ? "" : " - " + row.detail());
    }

    private static long number(String value) {
        try {
            return value == null || value.isBlank() ? 0 : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

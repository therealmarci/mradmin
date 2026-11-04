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
 * interface, from the storage) - newest first, mradmin.web.audit.
 * <pre>
 *  GET /api/logs?sources=a,b&q=&since=&before=&limit=   {entries: [{time, source, event, text, color}], sources, counts}
 * </pre>
 * sources (empty = all): "web" = the web log, "mradmin" = MRadmin itself, otherwise the id of a module. before = load
 * older ones. counts = entries per source in the range that match the text (only on the first page).
 */
public final class LogRoutes {

    private static final int MAX_LIMIT = 500;
    private static final int WEB_COLOR = 0x3B82F6;

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
        if (!server.permissions().has(http.account().id(), Perms.WEB_AUDIT)) {
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
        String q = query.get("q");
        long since = number(query.get("since"));
        long before = number(query.get("before"));
        int limit = (int) Math.clamp(number(query.get("limit")) <= 0 ? 200 : number(query.get("limit")), 1, MAX_LIMIT);
        List<EventLog.Entry> entries = new ArrayList<>();
        if (all || chosen.stream().anyMatch(c -> !c.equals("web"))) {
            entries.addAll(plugin.eventLog().find(all ? null : chosen, q, since, before, limit));
        }
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        int webCount = 0;
        List<Storage.AuditRow> audit = plugin.storage().recentAudit(2000);
        for (Storage.AuditRow row : audit) {
            if (row.time() >= since && (needle.isEmpty() || webText(row).toLowerCase(Locale.ROOT).contains(needle))) {
                webCount++;
            }
        }
        if (all || chosen.contains("web")) {
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
        entries.sort(Comparator.comparingLong(EventLog.Entry::time).reversed());
        List<Map<String, Object>> out = new ArrayList<>();
        for (EventLog.Entry e : entries.subList(0, Math.min(limit, entries.size()))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", e.time());
            m.put("source", e.source());
            m.put("name", e.source().equals("web") || e.source().equals("mradmin") ? e.source() : plugin.modules().displayName(e.source()));
            m.put("event", e.event());
            m.put("text", e.text());
            m.put("color", String.format("#%06X", e.color()));
            out.add(m);
        }
        Set<String> sources = new LinkedHashSet<>(List.of("mradmin", "web"));
        sources.addAll(plugin.eventLog().sources());
        Map<String, String> names = new LinkedHashMap<>();
        for (String s : sources) {
            names.put(s, s.equals("web") || s.equals("mradmin") ? s : plugin.modules().displayName(s));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entries", out);
        result.put("sources", names);
        result.put("more", entries.size() > limit);
        if (before <= 0) {
            Map<String, Integer> counts = new LinkedHashMap<>(plugin.eventLog().counts(q, since));
            counts.put("web", webCount);
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

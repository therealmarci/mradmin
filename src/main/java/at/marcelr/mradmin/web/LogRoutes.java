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
 *  GET /api/logs?source=&q=&since=&before=&limit=   {entries: [{time, source, event, text, color}], sources}
 * </pre>
 * source "web" = the web log, "mradmin" = MRadmin itself, otherwise the id of a module. before = load older ones.
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
        String source = query.get("source");
        String q = query.get("q");
        long since = number(query.get("since"));
        long before = number(query.get("before"));
        int limit = (int) Math.clamp(number(query.get("limit")) <= 0 ? 200 : number(query.get("limit")), 1, MAX_LIMIT);
        List<EventLog.Entry> entries = new ArrayList<>();
        if (source == null || source.isEmpty() || !source.equals("web")) {
            entries.addAll(plugin.eventLog().find(source, q, since, before, limit));
        }
        if (source == null || source.isEmpty() || source.equals("web")) {
            String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
            for (Storage.AuditRow row : plugin.storage().recentAudit(2000)) {
                if (row.time() < since || (before > 0 && row.time() >= before)) {
                    continue;
                }
                String text = row.name() + ": " + row.action() + (row.detail() == null || row.detail().isEmpty() ? "" : " - " + row.detail());
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
        http.json(200, Map.of("entries", out, "sources", names, "more", entries.size() > limit));
    }

    private static long number(String value) {
        try {
            return value == null || value.isBlank() ? 0 : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

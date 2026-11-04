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
 *  GET /api/logs?sources=a,b&q=&since=&until=&offset=&limit=&buckets=
 *      {entries: [{time, source, name, event, text, color}], total, counts, sources, chart}
 * </pre>
 * sources (empty = all): "web" = the web log, "mradmin" = MRadmin itself, "console", otherwise the id of a module.
 * One pass over the range [since, until) gives one page (offset, limit), the number of matches (total), the matches per
 * source regardless of the chosen sources (counts, for the source menu) and - with buckets - the activity chart:
 * {start, step, bars: [[events, info, warn, error], ...]} (console lines by level, everything else as events).
 */
public final class LogRoutes {

    private static final int MAX_LIMIT = 100;
    private static final int MAX_OFFSET = 20_000;
    private static final int MAX_BUCKETS = 200;
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

    /** Counts and chart bars of one pass. */
    private static final class Tally {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        final long start;
        final long step;
        final int[][] bars;
        int total;

        Tally(long start, long until, int buckets) {
            this.start = start;
            this.step = buckets <= 0 ? 1 : Math.max(1, (until - start + buckets - 1) / buckets);
            this.bars = new int[Math.max(0, buckets)][4];
        }

        void count(String source) {
            counts.merge(source, 1, Integer::sum);
        }

        /** column: 0 events, 1 info, 2 warn, 3 error */
        void chosen(long time, int column) {
            total++;
            if (bars.length > 0) {
                int i = (int) Math.clamp((time - start) / step, 0, bars.length - 1);
                bars[i][column]++;
            }
        }
    }

    private void list(Http http) throws Exception {
        boolean events = server.permissions().has(http.account().id(), Perms.WEB_AUDIT);
        boolean console = ConsoleRoutes.enabled(plugin) && server.permissions().has(http.account().id(), Perms.WEB_CONSOLE_VIEW);
        if (!events && !console) {
            throw new Http.WebException(403, "no-permission");
        }
        Map<String, String> query = http.query();
        Set<String> chosen = new LinkedHashSet<>();
        for (String s : query.getOrDefault("sources", "").split(",")) {
            if (!s.isBlank()) {
                chosen.add(s.strip());
            }
        }
        boolean all = chosen.isEmpty();
        String q = query.get("q");
        long now = System.currentTimeMillis();
        long since = Math.max(0, number(query.get("since")));
        long until = number(query.get("until")) <= 0 ? now + 1 : number(query.get("until"));
        int offset = (int) Math.clamp(number(query.get("offset")), 0, MAX_OFFSET);
        int limit = (int) Math.clamp(number(query.get("limit")) <= 0 ? 50 : number(query.get("limit")), 1, MAX_LIMIT);
        int keep = offset + limit;
        Tally tally = new Tally(since, until, (int) Math.clamp(number(query.get("buckets")), 0, MAX_BUCKETS));

        // Each source newest first; the first "keep" of each are enough for the page after merging.
        List<EventLog.Entry> top = new ArrayList<>();
        if (events) {
            int kept = 0;
            for (EventLog.Entry e : plugin.eventLog().find(null, q, since, until, Integer.MAX_VALUE)) {
                tally.count(e.source());
                if (all || chosen.contains(e.source())) {
                    tally.chosen(e.time(), 0);
                    if (kept++ < keep) {
                        top.add(e);
                    }
                }
            }
            String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
            kept = 0;
            for (Storage.AuditRow row : plugin.storage().recentAudit(2000)) {
                String text = webText(row);
                if (row.time() < since || row.time() >= until || (!needle.isEmpty() && !text.toLowerCase(Locale.ROOT).contains(needle))) {
                    continue;
                }
                tally.count("web");
                if (all || chosen.contains("web")) {
                    tally.chosen(row.time(), 0);
                    if (kept++ < keep) {
                        top.add(new EventLog.Entry(row.time(), "web", row.action(), text, WEB_COLOR));
                    }
                }
            }
        }
        if (console) {
            java.time.ZoneId zone = java.time.ZoneId.systemDefault();
            ServerLogFiles files = new ServerLogFiles(plugin.getServer().getWorldContainer().toPath().resolve("logs"), zone);
            boolean wanted = all || chosen.contains("console");
            int[] kept = {0};
            files.scan(java.time.LocalDate.now(zone), since, until, q,
                    plugin.getConfig().getBoolean("web.console.hide-links", true),
                    plugin.getConfig().getBoolean("web.console.hide-ips", true), line -> {
                        tally.count("console");
                        if (wanted) {
                            tally.chosen(line.key(), switch (line.level()) {
                                case "warn" -> 2;
                                case "error" -> 3;
                                default -> 1;
                            });
                            // key = time plus a running number within the second - keeps the order exact
                            if (kept[0]++ < keep) {
                                top.add(new EventLog.Entry(line.key(), "console", line.level(), line.text(),
                                        CONSOLE_COLORS.get(line.level())));
                            }
                        }
                        return true;
                    });
        }
        top.sort(Comparator.comparingLong(EventLog.Entry::time).reversed());

        List<Map<String, Object>> out = new ArrayList<>();
        for (EventLog.Entry e : top.subList(Math.min(offset, top.size()), Math.min(keep, top.size()))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", e.time());
            m.put("source", e.source());
            m.put("name", name(e.source()));
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
            names.put(s, name(s));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entries", out);
        result.put("total", tally.total);
        result.put("offset", offset);
        result.put("limit", limit);
        result.put("counts", tally.counts);
        result.put("sources", names);
        if (tally.bars.length > 0) {
            result.put("chart", Map.of("start", tally.start, "step", tally.step, "bars", tally.bars));
        }
        http.json(200, result);
    }

    private String name(String source) {
        return source.equals("web") || source.equals("mradmin") || source.equals("console") ? source
                : plugin.modules().displayName(source);
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

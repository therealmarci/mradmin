package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.api.AdminModule;
import at.marcelr.mradmin.api.SelfArea;
import at.marcelr.mradmin.api.SelfBlock;
import at.marcelr.mradmin.api.SelfViewer;
import at.marcelr.mradmin.maintenance.MaintenanceManager;
import at.marcelr.mradmin.maintenance.Window;
import at.marcelr.mradmin.news.KumaIncidents;
import at.marcelr.mradmin.news.News;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.ticket.TicketManager;
import at.marcelr.mradmin.web.Http;
import at.marcelr.mradmin.web.WebServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What is going on, for the slideshow on the start page of the player area and its page "News": planned or running
 * maintenance, a planned restart, warnings of the plugins about the viewer's things (Text blocks with tone warn/bad in
 * the summary of their areas, e.g. MRchunks: loaders locked for lag), tickets with an answer the viewer has not read yet
 * and the posts of the team (news, changelogs) in the viewer's language - the most urgent first.
 * <pre>
 *  GET /api/self/news?lang=de         the slideshow: {seconds, slides: [...]}
 *  GET /api/self/news?lang=de&amp;all=1   page "News": {alerts, news, changelog}
 * </pre>
 */
public final class SelfNews {

    private final WebServer server;
    private final MRAdmin plugin;
    private final SelfRoutes routes;
    private final SelfTickets tickets;

    SelfNews(WebServer server, SelfRoutes routes, SelfTickets tickets) {
        this.server = server;
        this.plugin = server.plugin();
        this.routes = routes;
        this.tickets = tickets;
    }

    void register() {
        server.self("GET", "/api/self/news", this::news);
    }

    boolean available() {
        return plugin.settings().playerWeb().news();
    }

    private void news(Http http) throws Exception {
        routes.check(http);
        if (!available()) {
            throw new Http.WebException(404, "not-found");
        }
        Settings.PlayerWebSettings settings = plugin.settings().playerWeb();
        String lang = "de".equals(http.query().get("lang")) ? "de" : "en";
        List<News.Post> posts = server.news().visible();
        // Warnings: written in MRadmin, or pinned on an Uptime Kuma status page.
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (News.Post p : server.news().activeAlerts()) {
            alerts.add(post(p, lang));
        }
        for (KumaIncidents.Incident i : plugin.newsDelivery().incidents().current()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "incident");
            m.put("id", "kuma-" + i.key());
            m.put("title", i.title());
            m.put("text", i.content());
            m.put("critical", i.critical());
            m.put("level", i.level());
            m.put("created", i.created());
            m.put("link", plugin.settings().kuma().url() + "/status/" + i.slug());
            alerts.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if ("1".equals(http.query().get("all"))) {
            // The page "News": warnings on top, news and changelogs separately.
            result.put("alerts", alerts);
            result.put("news", posts.stream().filter(p -> "news".equals(p.kind())).map(p -> post(p, lang)).toList());
            result.put("changelog", posts.stream().filter(p -> "changelog".equals(p.kind()))
                    .sorted(java.util.Comparator.comparingLong(News.Post::created).reversed()).map(p -> post(p, lang)).toList());
            http.json(200, result);
            return;
        }
        // The slideshow: the most urgent first.
        List<Map<String, Object>> slides = new ArrayList<>(alerts);
        if (settings.newsMaintenance() && plugin.feature(Feature.MAINTENANCE)) {
            Map<String, Object> maintenance = server.sync(this::maintenance);
            if (maintenance != null) {
                slides.add(maintenance);
            }
        }
        if (settings.newsRestart()) {
            long restartAt = server.sync(() -> plugin.restarts().endsAt());
            if (restartAt > 0) {
                slides.add(Map.of("type", "restart", "time", restartAt));
            }
        }
        if (settings.newsPluginNotices()) {
            slides.addAll(cachedNotices(http, lang));
        }
        if (settings.newsTickets() && tickets.available(http.player().player())) {
            slides.addAll(server.sync(() -> unreadTickets(http.player().player())));
        }
        List<News.Post> shown = posts.stream().filter(p -> !p.alert()).toList();
        for (News.Post p : shown.subList(0, Math.min(shown.size(), settings.newsSlides()))) {
            Map<String, Object> m = post(p, lang);
            m.put("text", p.preview(lang, 400));
            slides.add(m);
        }
        result.put("seconds", settings.newsSeconds());
        result.put("more", shown.size() > settings.newsSlides());
        result.put("slides", slides);
        http.json(200, result);
    }

    /** A post in the viewer's language; a changelog with its entries. */
    private static Map<String, Object> post(News.Post p, String lang) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", p.kind());
        m.put("id", p.id());
        m.put("title", p.heading(lang));
        m.put("text", p.in(p.text(), lang));
        m.put("version", p.version());
        List<Map<String, Object>> entries = new ArrayList<>();
        for (News.Entry e : p.entries()) {
            entries.add(Map.of("type", e.type(), "text", p.in(e.text(), lang)));
        }
        m.put("entries", entries);
        m.put("pinned", p.pinned());
        m.put("created", p.created());
        m.put("updated", p.updated());
        m.put("expires", p.expires());
        return m;
    }

    /** How long the plugins' warnings of a player are kept for the slideshow (it reloads every 20 seconds). */
    private static final long NOTICES_MILLIS = 45_000L;

    private record CachedNotices(long at, List<Map<String, Object>> notices) {
    }

    /** player + language -> the plugins' warnings, so the slideshow does not build every area on every reload. */
    private final Map<String, CachedNotices> noticeCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** After an action of the player (e.g. a loader switched): their warnings are built again on the next reload. */
    void forget(UUID player) {
        noticeCache.keySet().removeIf(k -> k.startsWith(player + ":"));
    }

    private List<Map<String, Object>> cachedNotices(Http http, String lang) throws Exception {
        String key = http.player().player() + ":" + lang;
        long now = System.currentTimeMillis();
        CachedNotices cached = noticeCache.get(key);
        if (cached != null && now - cached.at() < NOTICES_MILLIS) {
            return cached.notices();
        }
        noticeCache.values().removeIf(c -> now - c.at() >= NOTICES_MILLIS);
        List<Map<String, Object>> fresh = notices(http);
        noticeCache.put(key, new CachedNotices(now, fresh));
        return fresh;
    }

    /** Warnings of the plugins: Text blocks with tone bad (first) or warn in the summary of their areas. */
    private List<Map<String, Object>> notices(Http http) throws Exception {
        List<Map<String, Object>> bad = new ArrayList<>();
        List<Map<String, Object>> warn = new ArrayList<>();
        for (var entry : plugin.modules().webModules().entrySet()) {
            SelfViewer viewer = routes.viewer(http, entry.getKey());
            AdminModule module = plugin.modules().module(entry.getKey());
            List<SelfArea> areas = server.sync(() -> plugin.modules().call(module,
                    () -> entry.getValue().selfAreas(viewer), List.<SelfArea>of()));
            for (SelfArea area : areas) {
                for (SelfBlock block : area.summary()) {
                    if (block instanceof SelfBlock.Text text && ("bad".equals(text.tone()) || "warn".equals(text.tone()))) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("type", "notice");
                        m.put("tone", text.tone());
                        m.put("title", area.title());
                        m.put("icon", area.icon());
                        m.put("text", text.text());
                        m.put("page", "/me/" + entry.getKey() + "/" + area.id());
                        ("bad".equals(text.tone()) ? bad : warn).add(m);
                    }
                }
            }
        }
        bad.addAll(warn);
        return bad;
    }

    /** Own tickets with something new from the team (answer or closed). Main thread. */
    private List<Map<String, Object>> unreadTickets(UUID player) {
        List<Map<String, Object>> result = new ArrayList<>();
        TicketManager manager = plugin.tickets();
        for (Ticket t : manager.byCreator(player)) {
            if (t.visibleInGame() && t.creatorUnread()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "ticket");
                m.put("id", t.id());
                m.put("title", manager.summary(t));
                m.put("open", t.open());
                m.put("time", t.lastActivity().toEpochMilli());
                result.add(m);
            }
        }
        return result;
    }

    /** Planned, coming or running maintenance - null without one. Main thread. */
    private Map<String, Object> maintenance() {
        MaintenanceManager m = plugin.maintenance();
        Window w = m.current();
        if (w == null || m.phase() == MaintenanceManager.Phase.NONE) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "maintenance");
        result.put("phase", m.phase().name().toLowerCase(Locale.ROOT));
        result.put("title", w.title() == null ? "" : w.title());
        result.put("start", w.start() == null ? 0 : w.start().toEpochMilli());
        result.put("end", w.end() == null ? 0 : w.end().toEpochMilli());
        return result;
    }
}

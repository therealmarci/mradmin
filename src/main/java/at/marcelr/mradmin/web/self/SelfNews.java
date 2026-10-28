package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.maintenance.MaintenanceManager;
import at.marcelr.mradmin.maintenance.Window;
import at.marcelr.mradmin.news.News;
import at.marcelr.mradmin.web.Http;
import at.marcelr.mradmin.web.WebServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What is going on, for the slideshow on the start page of the player area and its page "News": planned or running
 * maintenance, a planned restart and the posts of the team (news, changelogs) in the viewer's language.
 * <pre>
 *  GET /api/self/news?lang=de         the slideshow: {seconds, slides: [...]}
 *  GET /api/self/news?lang=de&amp;all=1   every visible post (page "News")
 * </pre>
 */
public final class SelfNews {

    private final WebServer server;
    private final MRAdmin plugin;
    private final SelfRoutes routes;

    SelfNews(WebServer server, SelfRoutes routes) {
        this.server = server;
        this.plugin = server.plugin();
        this.routes = routes;
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
        boolean all = "1".equals(http.query().get("all"));
        List<Map<String, Object>> slides = new ArrayList<>();
        if (!all) {
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
        }
        List<News.Post> posts = server.news().visible();
        for (News.Post p : all ? posts : posts.subList(0, Math.min(posts.size(), settings.newsSlides()))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", p.kind());
            m.put("id", p.id());
            m.put("title", p.in(p.title(), lang));
            m.put("text", p.in(p.text(), lang));
            m.put("pinned", p.pinned());
            m.put("created", p.created());
            m.put("updated", p.updated());
            slides.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("seconds", settings.newsSeconds());
        result.put("more", posts.size() > settings.newsSlides());
        result.put("slides", slides);
        http.json(200, result);
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

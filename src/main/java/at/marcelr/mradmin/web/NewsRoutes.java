package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.news.News;
import at.marcelr.mradmin.storage.Storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * News and changelogs for the players' own page, written by the team (mradmin.news). Shown in the slideshow of the
 * player area (/web) and on its page "News".
 * <pre>
 *  GET  /api/news                    all posts (also expired ones)
 *  POST /api/news   {id?, kind, pinned, expires, title: {de, en}, text: {de, en}}   create (id 0/missing) or change
 *  POST /api/news/{id}/delete
 * </pre>
 * At least one language needs a title and a text; the other one falls back to it. Everything goes into the web log.
 */
public final class NewsRoutes {

    private final WebServer server;
    private final MRAdmin plugin;

    private NewsRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        NewsRoutes r = new NewsRoutes(server);
        server.route("GET", "/api/news", r::list);
        server.route("POST", "/api/news", r::save);
        server.route("POST", "/api/news/{id}/delete", r::delete);
    }

    private void require(Http http) throws Http.WebException {
        if (!server.permissions().has(http.account().id(), Perms.NEWS)) {
            throw new Http.WebException(403, "no-permission");
        }
    }

    private void list(Http http) throws Exception {
        require(http);
        List<Map<String, Object>> posts = new ArrayList<>();
        for (News.Post p : server.news().all()) {
            posts.add(json(p));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("posts", posts);
        // Posts are only shown on the players' own page - say so if it is off.
        result.put("playerWeb", plugin.settings().playerWeb().enabled() && plugin.settings().playerWeb().news());
        http.json(200, result);
    }

    static Map<String, Object> json(News.Post p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("kind", p.kind());
        m.put("title", p.title());
        m.put("text", p.text());
        m.put("pinned", p.pinned());
        m.put("created", p.created());
        m.put("updated", p.updated());
        m.put("expires", p.expires());
        m.put("author", p.author());
        return m;
    }

    private void save(Http http) throws Exception {
        require(http);
        Map<String, Object> body = http.bodyTree();
        int id = body.get("id") instanceof Long l ? l.intValue() : 0;
        String kind = News.KINDS.contains(body.get("kind")) ? (String) body.get("kind") : "news";
        Map<String, String> title = texts(body.get("title"), News.MAX_TITLE);
        Map<String, String> text = texts(body.get("text"), News.MAX_TEXT);
        if (News.LANGS.stream().noneMatch(lang -> title.containsKey(lang) && text.containsKey(lang))) {
            throw new Http.WebException(400, "news-empty");
        }
        long expires = body.get("expires") instanceof Long l && l > 0 ? l : 0;
        if (id > 0 && server.news().get(id) == null) {
            throw new Http.WebException(404, "not-found");
        }
        News.Post post = server.news().save(id, kind, title, text, Boolean.TRUE.equals(body.get("pinned")), expires,
                http.account().name());
        audit(http, id > 0 ? "news-edit" : "news-add", "#" + post.id() + " " + post.in(post.title(), "de"));
        http.json(200, json(post));
    }

    /** {de: "...", en: "..."} -> only filled languages, cut to the maximum length. */
    private static Map<String, String> texts(Object value, int max) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (String lang : News.LANGS) {
                if (map.get(lang) instanceof String s && !s.isBlank()) {
                    String clean = s.strip().replace("\r\n", "\n");
                    result.put(lang, clean.length() > max ? clean.substring(0, max) : clean);
                }
            }
        }
        return result;
    }

    private void delete(Http http) throws Exception {
        require(http);
        int id;
        try {
            id = Integer.parseInt(http.param("id"));
        } catch (NumberFormatException e) {
            throw new Http.WebException(404, "not-found");
        }
        News.Post post = server.news().get(id);
        if (post == null || !server.news().delete(id)) {
            throw new Http.WebException(404, "not-found");
        }
        audit(http, "news-delete", "#" + id + " " + post.in(post.title(), "de"));
    }

    private void audit(Http http, String action, String detail) {
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), http.account().id(), http.account().name(),
                http.storedIp(), action, detail));
    }
}

package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.news.KumaIncidents;
import at.marcelr.mradmin.news.News;
import at.marcelr.mradmin.storage.Storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * News, changelogs and critical warnings for the players, written by the team (mradmin.news). Shown on the player page
 * (/web), optionally posted to the community Discord, previewed in game (see
 * {@link at.marcelr.mradmin.news.NewsDelivery}).
 * <pre>
 *  GET  /api/news                    all posts (also expired and ended ones), the current Uptime Kuma incidents
 *  POST /api/news   {id?, kind, pinned, expires, title: {de, en}, text: {de, en}, version, entries: [{type, de, en}] (type also h1/h2/h3 = heading),
 *                    discord}        create (id 0/missing) or change; discord = post it there (once)
 *  POST /api/news/{id}/end           ends a warning
 *  POST /api/news/{id}/delete
 * </pre>
 * At least one language needs the content (news, warning: title and text; changelog: a version and one entry); the
 * other language falls back to it. Everything goes into the web log.
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
        server.route("POST", "/api/news/{id}/end", r::end);
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
        List<Map<String, Object>> incidents = new ArrayList<>();
        for (KumaIncidents.Incident i : plugin.newsDelivery().incidents().current()) {
            incidents.add(Map.of("key", i.key(), "slug", i.slug(), "title", i.title(), "content", i.content(), "level", i.level(),
                    "created", i.created()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("posts", posts);
        result.put("incidents", incidents);
        // Where it shows up - so the page can say what is switched off.
        result.put("playerWeb", plugin.settings().playerWeb().enabled() && plugin.settings().playerWeb().news());
        result.put("discord", plugin.discordAnnounce() != null && plugin.discordAnnounce().enabled());
        http.json(200, result);
    }

    static Map<String, Object> json(News.Post p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("kind", p.kind());
        m.put("title", p.title());
        m.put("text", p.text());
        m.put("version", p.version());
        List<Map<String, Object>> entries = new ArrayList<>();
        for (News.Entry e : p.entries()) {
            Map<String, Object> entry = new LinkedHashMap<>(e.text());
            entry.put("type", e.type());
            entries.add(entry);
        }
        m.put("entries", entries);
        m.put("pinned", p.pinned());
        m.put("discord", p.discord());
        m.put("created", p.created());
        m.put("updated", p.updated());
        m.put("expires", p.expires());
        m.put("ended", p.ended());
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
        String version = body.get("version") instanceof String v ? cut(v.strip(), 40) : "";
        List<News.Entry> entries = new ArrayList<>();
        if (body.get("entries") instanceof List<?> list) {
            for (Object raw : list) {
                if (entries.size() < News.MAX_ENTRIES && raw instanceof Map<?, ?> e) {
                    Map<String, String> t = texts(e, News.MAX_ENTRY);
                    if (!t.isEmpty()) {
                        entries.add(new News.Entry(News.validType(e.get("type")) ? (String) e.get("type") : "changed", t));
                    }
                }
            }
        }
        // A changelog needs at least one entry besides its headings.
        boolean complete = "changelog".equals(kind) ? !version.isEmpty() && entries.stream().anyMatch(e -> !e.heading())
                : News.LANGS.stream().anyMatch(lang -> title.containsKey(lang) && text.containsKey(lang));
        if (!complete) {
            throw new Http.WebException(400, "changelog".equals(kind) ? "news-changelog-empty" : "news-empty");
        }
        long expires = body.get("expires") instanceof Long l && l > 0 ? l : 0;
        News.Post old = id > 0 ? server.news().get(id) : null;
        if (id > 0 && old == null) {
            throw new Http.WebException(404, "not-found");
        }
        boolean pinned = !"alert".equals(kind) && Boolean.TRUE.equals(body.get("pinned"));
        News.Post post = server.news().save(id, kind, title, text, version, entries, pinned, expires, http.account().name());
        boolean discord = Boolean.TRUE.equals(body.get("discord")) && !post.discord();
        // New: to the players (chat preview, warning in game) and, if asked for, Discord. Changed: only Discord if
        // it is asked for now and was not posted yet.
        server.sync(() -> {
            if (old == null) {
                plugin.newsDelivery().published(post, discord);
            } else if (discord && plugin.newsDelivery().discord(post)) {
                server.news().markDiscord(post.id());
            }
            return null;
        });
        audit(http, (id > 0 ? "news-edit" : "news-add") + "-" + kind, "#" + post.id() + " " + post.heading("de"));
        http.json(200, json(server.news().get(post.id())));
    }

    /** {de: "...", en: "..."} -> only filled languages, cut to the maximum length. */
    private static Map<String, String> texts(Object value, int max) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (String lang : News.LANGS) {
                if (map.get(lang) instanceof String s && !s.isBlank()) {
                    result.put(lang, cut(s.strip().replace("\r\n", "\n"), max));
                }
            }
        }
        return result;
    }

    private static String cut(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private int id(Http http) throws Http.WebException {
        try {
            return Integer.parseInt(http.param("id"));
        } catch (NumberFormatException e) {
            throw new Http.WebException(404, "not-found");
        }
    }

    private void end(Http http) throws Exception {
        require(http);
        int id = id(http);
        if (!server.news().end(id)) {
            throw new Http.WebException(404, "not-found");
        }
        audit(http, "news-end-alert", "#" + id + " " + server.news().get(id).heading("de"));
    }

    private void delete(Http http) throws Exception {
        require(http);
        int id = id(http);
        News.Post post = server.news().get(id);
        if (post == null || !server.news().delete(id)) {
            throw new Http.WebException(404, "not-found");
        }
        audit(http, "news-delete", "#" + id + " " + post.heading("de"));
    }

    private void audit(Http http, String action, String detail) {
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), http.account().id(), http.account().name(),
                http.storedIp(), action, detail));
    }
}

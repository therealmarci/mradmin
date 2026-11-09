package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.news.News;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.web.Http;
import at.marcelr.mradmin.web.WebServer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The inbox of the player area (the letter at the top right): news and changelogs of the team and the viewer's own
 * tickets in one place. Here only the counter - what is new since the viewer last looked - the contents come from
 * {@link SelfNews} and {@link SelfTickets}.
 * <pre>
 *  GET  /api/self/inbox         {posts, tickets, total, seen}: posts newer than the last visit, tickets with an
 *                               unread answer of the team
 *  POST /api/self/inbox/seen    the viewer looked at the posts now (tickets count as read when opened)
 * </pre>
 */
final class SelfInbox {

    /** Storage of the last visit per player (epoch ms). */
    private static final String FILE = "inbox-seen";
    /** Never looked yet: only posts of the last two weeks count as new, not everything ever written. */
    private static final long FIRST_LOOK_MILLIS = 14L * 24 * 60 * 60 * 1000;

    private final WebServer server;
    private final MRAdmin plugin;
    private final SelfRoutes routes;
    private final SelfNews news;
    private final SelfTickets tickets;
    private final Map<UUID, Long> seen = new HashMap<>();
    private boolean loaded;

    SelfInbox(WebServer server, SelfRoutes routes, SelfNews news, SelfTickets tickets) {
        this.server = server;
        this.plugin = server.plugin();
        this.routes = routes;
        this.news = news;
        this.tickets = tickets;
    }

    void register() {
        server.self("GET", "/api/self/inbox", this::count);
        server.self("POST", "/api/self/inbox/seen", this::markSeen);
    }

    /** Something to show in the inbox: posts or own tickets. */
    boolean available(UUID player) {
        return news.available() || tickets.available(player);
    }

    private void count(Http http) throws Exception {
        routes.check(http);
        UUID player = http.player().player();
        long since = seen(player);
        int posts = 0;
        if (news.available()) {
            for (News.Post p : server.news().visible()) {
                if (!p.alert() && Math.max(p.created(), p.updated()) > since) {
                    posts++;
                }
            }
        }
        int answers = 0;
        if (tickets.available(player)) {
            answers = server.sync(() -> {
                int n = 0;
                for (Ticket t : plugin.tickets().byCreator(player)) {
                    if (t.visibleInGame() && t.creatorUnread()) {
                        n++;
                    }
                }
                return n;
            });
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("posts", posts);
        result.put("tickets", answers);
        result.put("total", posts + answers);
        result.put("seen", since);
        http.json(200, result);
    }

    private void markSeen(Http http) throws Exception {
        routes.check(http);
        UUID player = http.player().player();
        long now = System.currentTimeMillis();
        synchronized (seen) {
            load();
            seen.put(player, now);
            YamlConfiguration y = new YamlConfiguration();
            y.options().setHeader(java.util.List.of("When each player last looked at the posts in the inbox of the web "
                    + "interface (managed by the plugin)."));
            for (Map.Entry<UUID, Long> e : seen.entrySet()) {
                y.set(e.getKey().toString(), e.getValue());
            }
            plugin.storage().saveYaml(FILE, y);
        }
        http.json(200, Map.of("seen", now));
    }

    private long seen(UUID player) {
        synchronized (seen) {
            load();
            Long at = seen.get(player);
            return at != null ? at : System.currentTimeMillis() - FIRST_LOOK_MILLIS;
        }
    }

    private void load() {
        if (loaded) {
            return;
        }
        YamlConfiguration y = plugin.storage().loadYaml(FILE, plugin.getLogger());
        for (String key : y.getKeys(false)) {
            try {
                seen.put(UUID.fromString(key), y.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // broken entry
            }
        }
        loaded = true;
    }
}

package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.news.News;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.ticket.TicketManager;
import at.marcelr.mradmin.web.Http;
import at.marcelr.mradmin.web.WebServer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The inbox of the player area (the letter at the top right): notifications, newest first - a new news post, a new
 * changelog, an answer of the team to an own ticket. Each one is read or unread; a click opens it in a window, the
 * whole list of posts and tickets is in the entry "News" ({@link SelfNews}, {@link SelfTickets}).
 * <pre>
 *  GET  /api/self/inbox?lang=de    {items: [{key, kind, id, title, version?, open?, time, read}], unread, total}
 *  POST /api/self/inbox/read       {key} one notification read, {all: true} all of them (also the ticket answers)
 * </pre>
 * Read state per player in the storage "inbox-seen": a point in time (everything up to it counts as read) and the
 * keys read one by one with the time. Ticket answers are read when the ticket was opened (ticket store).
 */
final class SelfInbox {

    /** Storage of the read state per player. */
    private static final String FILE = "inbox-seen";
    private static final long DAY = 24L * 60 * 60 * 1000;
    /** Never looked yet: only posts of the last two weeks count as unread, not everything ever written. */
    private static final long FIRST_LOOK_MILLIS = 14 * DAY;
    /** Posts and ticket answers older than this are no notification any more. */
    private static final long MAX_AGE = 60 * DAY;
    private static final int MAX_POSTS = 30;

    /** Read state of one player: everything up to {@code all} plus single keys (key -> read at). */
    private record State(long all, Map<String, Long> keys) {
        boolean read(String key, long time) {
            Long at = keys.get(key);
            return time <= all || (at != null && at >= time);
        }
    }

    private final WebServer server;
    private final MRAdmin plugin;
    private final SelfRoutes routes;
    private final SelfNews news;
    private final SelfTickets tickets;
    private final Map<UUID, State> states = new HashMap<>();
    private boolean loaded;

    SelfInbox(WebServer server, SelfRoutes routes, SelfNews news, SelfTickets tickets) {
        this.server = server;
        this.plugin = server.plugin();
        this.routes = routes;
        this.news = news;
        this.tickets = tickets;
    }

    void register() {
        server.self("GET", "/api/self/inbox", this::list);
        server.self("POST", "/api/self/inbox/read", this::markRead);
    }

    /** Something to show in the inbox: posts or own tickets. */
    boolean available(UUID player) {
        return news.available() || tickets.available(player);
    }

    private void list(Http http) throws Exception {
        routes.check(http);
        UUID player = http.player().player();
        String lang = "de".equals(http.query().get("lang")) ? "de" : "en";
        State state = state(player);
        long now = System.currentTimeMillis();
        List<Map<String, Object>> items = new ArrayList<>();
        if (news.available()) {
            List<News.Post> posts = new ArrayList<>();
            for (News.Post p : server.news().visible()) {
                if (!p.alert() && now - time(p) <= MAX_AGE) {
                    posts.add(p);
                }
            }
            posts.sort(Comparator.comparingLong(SelfInbox::time).reversed());
            for (News.Post p : posts.subList(0, Math.min(MAX_POSTS, posts.size()))) {
                String key = "post-" + p.id();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", key);
                m.put("kind", "changelog".equals(p.kind()) ? "changelog" : "news");
                m.put("id", p.id());
                m.put("title", p.heading(lang));
                if (p.version() != null && !p.version().isBlank()) {
                    m.put("version", p.version());
                }
                m.put("time", time(p));
                m.put("read", state.read(key, time(p)));
                items.add(m);
            }
        }
        if (tickets.available(player)) {
            items.addAll(server.sync(() -> {
                List<Map<String, Object>> list = new ArrayList<>();
                TicketManager manager = plugin.tickets();
                for (Ticket t : manager.byCreator(player)) {
                    Ticket.Message answer = lastAnswer(t);
                    if (!t.visibleInGame() || answer == null || now - answer.time().toEpochMilli() > MAX_AGE) {
                        continue;
                    }
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("key", "ticket-" + t.id());
                    m.put("kind", "ticket");
                    m.put("id", t.id());
                    m.put("title", manager.summary(t));
                    m.put("open", t.open());
                    m.put("time", answer.time().toEpochMilli());
                    m.put("read", !t.creatorUnread());
                    list.add(m);
                }
                return list;
            }));
        }
        items.sort(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("time")).reversed());
        long unread = items.stream().filter(m -> !(Boolean) m.get("read")).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("unread", unread);
        result.put("total", unread);
        http.json(200, result);
    }

    /** The last message of the team (or a note of the system) after the player's own, null without one. */
    private static Ticket.Message lastAnswer(Ticket t) {
        Ticket.Message last = null;
        for (Ticket.Message msg : t.messages()) {
            if (msg.staff() && !t.isCreator(msg.author())) {
                last = msg;
            }
        }
        return last;
    }

    private static long time(News.Post p) {
        return Math.max(p.created(), p.updated());
    }

    private void markRead(Http http) throws Exception {
        routes.check(http);
        UUID player = http.player().player();
        Map<String, Object> body = http.bodyValues();
        long now = System.currentTimeMillis();
        if (Boolean.TRUE.equals(body.get("all"))) {
            update(player, new State(now, new HashMap<>()));
            if (tickets.available(player)) {
                server.sync(() -> {
                    TicketManager manager = plugin.tickets();
                    for (Ticket t : manager.byCreator(player)) {
                        if (t.visibleInGame() && t.creatorUnread()) {
                            manager.webMarkRead(player, t);
                        }
                    }
                    return null;
                });
            }
        } else {
            String key = String.valueOf(body.getOrDefault("key", ""));
            if (!key.matches("post-\\d{1,9}")) {
                http.json(400, Map.of("error", "bad-request"));
                return;
            }
            State old = state(player);
            Map<String, Long> keys = new HashMap<>(old.keys());
            keys.put(key, now);
            update(player, new State(old.all(), keys));
        }
        http.json(200, Map.of("ok", true));
    }

    private State state(UUID player) {
        synchronized (states) {
            load();
            State s = states.get(player);
            return s != null ? s : new State(System.currentTimeMillis() - FIRST_LOOK_MILLIS, Map.of());
        }
    }

    private void update(UUID player, State state) {
        synchronized (states) {
            load();
            // single keys older than the oldest notification are not needed any more
            long limit = System.currentTimeMillis() - MAX_AGE;
            Map<String, Long> keys = new HashMap<>();
            state.keys().forEach((k, at) -> {
                if (at >= limit && at > state.all()) {
                    keys.put(k, at);
                }
            });
            states.put(player, new State(state.all(), keys));
            YamlConfiguration y = new YamlConfiguration();
            y.options().setHeader(List.of("Which notifications each player has read in the inbox of the web interface "
                    + "(managed by the plugin): 'all' = everything up to this time, 'read' = single ones."));
            for (Map.Entry<UUID, State> e : states.entrySet()) {
                String base = e.getKey().toString();
                y.set(base + ".all", e.getValue().all());
                e.getValue().keys().forEach((k, at) -> y.set(base + ".read." + k, at));
            }
            plugin.storage().saveYaml(FILE, y);
        }
    }

    private void load() {
        if (loaded) {
            return;
        }
        YamlConfiguration y = plugin.storage().loadYaml(FILE, plugin.getLogger());
        for (String key : y.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                if (y.isConfigurationSection(key)) {
                    Map<String, Long> keys = new HashMap<>();
                    ConfigurationSection read = y.getConfigurationSection(key + ".read");
                    if (read != null) {
                        for (String k : read.getKeys(false)) {
                            keys.put(k, read.getLong(k));
                        }
                    }
                    states.put(uuid, new State(y.getLong(key + ".all"), keys));
                } else {
                    // before: only the time of the last visit
                    states.put(uuid, new State(y.getLong(key), new HashMap<>()));
                }
            } catch (IllegalArgumentException ignored) {
                // broken entry
            }
        }
        loaded = true;
    }
}

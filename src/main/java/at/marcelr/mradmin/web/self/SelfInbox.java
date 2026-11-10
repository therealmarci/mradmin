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
 * changelog, an answer of the team to an own ticket, and for the team the team notices
 * ({@link at.marcelr.mradmin.notify.TeamNotices}: new ticket, application, crash, update ...). Each one is read or unread; a click opens it in a window, the
 * whole list of posts and tickets is in the entry "News" ({@link SelfNews}, {@link SelfTickets}).
 * <pre>
 *  GET  /api/self/inbox?lang=de    {items: [{key, kind, id, title, version?, open?, time, read, archived}], unread, total,
 *                                   alarm, team}; team notices: kind "team", notice, body, url, done, alarm
 *  POST /api/self/inbox/read       {key} one notification read, {all: true} all of them (also the ticket answers)
 *  POST /api/self/inbox/archive    {key} one notification into the archive, {all: true} all of the inbox
 *  POST /api/self/inbox/restore    {key} back from the archive
 * </pre>
 * Read state per player in the storage "inbox-seen": a point in time (everything up to it counts as read), the keys
 * read one by one and the archived keys, each with the time. Ticket answers are read when the ticket was opened
 * (ticket store). Something new (a new answer, a changed post) brings an archived notification back into the inbox.
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

    /** State of one player: read up to {@code all}, single keys read (key -> at), archived keys (key -> at). */
    private record State(long all, Map<String, Long> keys, Map<String, Long> archived) {
        boolean read(String key, long time) {
            Long at = keys.get(key);
            return time <= all || (at != null && at >= time);
        }

        boolean archived(String key, long time) {
            Long at = archived.get(key);
            return at != null && at >= time;
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
        if (plugin.notices() != null) {
            plugin.notices().unreadCounter(this::unreadTeam);
        }
        server.self("GET", "/api/self/inbox", this::list);
        server.self("POST", "/api/self/inbox/read", this::markRead);
        server.self("POST", "/api/self/inbox/archive", this::archive);
        server.self("POST", "/api/self/inbox/restore", this::restore);
    }

    /** Something to show in the inbox: posts or own tickets. */
    boolean available(UUID player) {
        return news.available() || tickets.available(player)
                || (plugin.notices() != null && !plugin.notices().of(player).isEmpty());
    }

    private void list(Http http) throws Exception {
        routes.check(http);
        UUID player = http.player().player();
        String lang = "de".equals(http.query().get("lang")) ? "de" : "en";
        List<Map<String, Object>> items = items(player, lang);
        long unread = items.stream().filter(SelfInbox::counts).count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("unread", unread);
        result.put("total", unread);
        // An unread crash turns the letter red; "team" shows the switch All | Team.
        result.put("alarm", items.stream().anyMatch(m -> counts(m) && Boolean.TRUE.equals(m.get("alarm"))));
        result.put("team", items.stream().anyMatch(m -> "team".equals(m.get("kind"))));
        http.json(200, result);
    }

    /** Unread, not archived and (team notices) not done. */
    private static boolean counts(Map<String, Object> m) {
        return !(Boolean) m.get("read") && !(Boolean) m.get("archived") && !Boolean.TRUE.equals(m.get("done"));
    }

    /** Team notices the member has not read (for the line after joining). Server thread. */
    private int unreadTeam(UUID player) {
        var notices = plugin.notices();
        State state = state(player);
        int count = 0;
        for (var n : notices.of(player)) {
            String key = "team-" + n.id();
            if (!state.read(key, n.time()) && !state.archived(key, n.time()) && !notices.done(n)) {
                count++;
            }
        }
        return count;
    }

    /** All notifications of a player, newest first, with read and archived. */
    private List<Map<String, Object>> items(UUID player, String lang) throws Exception {
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
        var notices = plugin.notices() == null ? List.<at.marcelr.mradmin.notify.TeamNotices.Notice>of()
                : plugin.notices().of(player);
        if (!notices.isEmpty()) {
            items.addAll(server.sync(() -> {
                List<Map<String, Object>> list = new ArrayList<>();
                for (var n : notices) {
                    String key = "team-" + n.id();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("key", key);
                    m.put("kind", "team");
                    m.put("notice", n.kind());
                    m.put("id", n.id());
                    m.put("title", n.title(lang));
                    m.put("body", n.body(lang));
                    m.put("url", n.url());
                    m.put("time", n.time());
                    m.put("read", state.read(key, n.time()));
                    m.put("done", plugin.notices().done(n));
                    m.put("alarm", plugin.notices().alarm(n));
                    list.add(m);
                }
                return list;
            }));
        }
        for (Map<String, Object> m : items) {
            m.put("archived", state.archived((String) m.get("key"), (Long) m.get("time")));
        }
        items.sort(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("time")).reversed());
        return items;
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
        return Math.max(p.published(), p.updated());
    }

    private void markRead(Http http) throws Exception {
        routes.check(http);
        UUID player = http.player().player();
        Map<String, Object> body = http.bodyValues();
        long now = System.currentTimeMillis();
        if (Boolean.TRUE.equals(body.get("all"))) {
            State old = state(player);
            update(player, new State(now, new HashMap<>(), old.archived()));
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
            if (!key.matches("(post|team)-\\d{1,12}")) {
                http.json(400, Map.of("error", "bad-request"));
                return;
            }
            State old = state(player);
            Map<String, Long> keys = new HashMap<>(old.keys());
            keys.put(key, now);
            update(player, new State(old.all(), keys, old.archived()));
        }
        http.json(200, Map.of("ok", true));
    }

    private void archive(Http http) throws Exception {
        routes.check(http);
        UUID player = http.player().player();
        Map<String, Object> body = http.bodyValues();
        long now = System.currentTimeMillis();
        State old = state(player);
        Map<String, Long> archived = new HashMap<>(old.archived());
        if (Boolean.TRUE.equals(body.get("all"))) {
            for (Map<String, Object> m : items(player, "en")) {
                archived.put((String) m.get("key"), now);
            }
        } else {
            String key = String.valueOf(body.getOrDefault("key", ""));
            if (!key.matches("(post|ticket|team)-\\d{1,12}")) {
                http.json(400, Map.of("error", "bad-request"));
                return;
            }
            archived.put(key, now);
        }
        update(player, new State(old.all(), old.keys(), archived));
        http.json(200, Map.of("ok", true));
    }

    private void restore(Http http) throws Exception {
        routes.check(http);
        UUID player = http.player().player();
        String key = String.valueOf(http.bodyValues().getOrDefault("key", ""));
        State old = state(player);
        Map<String, Long> archived = new HashMap<>(old.archived());
        archived.remove(key);
        update(player, new State(old.all(), old.keys(), archived));
        http.json(200, Map.of("ok", true));
    }

    private State state(UUID player) {
        synchronized (states) {
            load();
            State s = states.get(player);
            return s != null ? s : new State(System.currentTimeMillis() - FIRST_LOOK_MILLIS, Map.of(), Map.of());
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
            Map<String, Long> archived = new HashMap<>();
            state.archived().forEach((k, at) -> {
                if (at >= limit) {
                    archived.put(k, at);
                }
            });
            states.put(player, new State(state.all(), keys, archived));
            YamlConfiguration y = new YamlConfiguration();
            y.options().setHeader(List.of("Which notifications each player has read in the inbox of the web interface "
                    + "(managed by the plugin): 'all' = read up to this time, 'read' = single ones, 'archived' = in the archive."));
            for (Map.Entry<UUID, State> e : states.entrySet()) {
                String base = e.getKey().toString();
                y.set(base + ".all", e.getValue().all());
                e.getValue().keys().forEach((k, at) -> y.set(base + ".read." + k, at));
                e.getValue().archived().forEach((k, at) -> y.set(base + ".archived." + k, at));
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
                    Map<String, Long> archived = new HashMap<>();
                    ConfigurationSection arch = y.getConfigurationSection(key + ".archived");
                    if (arch != null) {
                        for (String k : arch.getKeys(false)) {
                            archived.put(k, arch.getLong(k));
                        }
                    }
                    states.put(uuid, new State(y.getLong(key + ".all"), keys, archived));
                } else {
                    // before: only the time of the last visit
                    states.put(uuid, new State(y.getLong(key), new HashMap<>(), new HashMap<>()));
                }
            } catch (IllegalArgumentException ignored) {
                // broken entry
            }
        }
        loaded = true;
    }
}

package at.marcelr.mradmin.news;

import at.marcelr.mradmin.MRAdmin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Posts of the team for the players: news, changelogs and critical warnings, each with texts per language (German,
 * English - a missing language shows the other one). Written in the web interface ("News", mradmin.news), shown on the
 * player page, optionally posted to the community Discord, previewed once in game (see {@link NewsDelivery}). Kept in
 * the storage file "news". Thread-safe.
 * <ul>
 *   <li><b>news</b>: title + text, may be pinned</li>
 *   <li><b>changelog</b>: version, optional title, entries of a type (new, changed, fixed, removed) and headings in
 *       three levels (h1 main heading, h2 sub heading, h3 a further level) - the entries after a heading belong to it</li>
 *   <li><b>alert</b>: a critical warning (title + text), active until it runs out or is ended</li>
 * </ul>
 */
public final class News {

    public static final int MAX_TITLE = 120;
    public static final int MAX_TEXT = 4000;
    public static final int MAX_ENTRY = 300;
    public static final int MAX_ENTRIES = 150;
    public static final List<String> KINDS = List.of("news", "changelog", "alert");
    public static final List<String> ENTRY_TYPES = List.of("new", "changed", "fixed", "removed");
    /** Headings in a changelog: main heading, sub heading, a further level. */
    public static final List<String> HEADINGS = List.of("h1", "h2", "h3");
    public static final List<String> LANGS = List.of("de", "en");
    private static final String FILE = "news";

    /** One line of a changelog: its type (an entry type or a heading) and the text per language. */
    public record Entry(String type, Map<String, String> text) {

        public boolean heading() {
            return HEADINGS.contains(type);
        }
    }

    /** Whether a changelog line may have this type (entry or heading). */
    public static boolean validType(Object type) {
        return ENTRY_TYPES.contains(type) || HEADINGS.contains(type);
    }

    /**
     * One post. {@code title}/{@code text}: language code -> text. {@code expires}: hidden (or, for a warning, over)
     * from this time on (ms, 0 = never); {@code ended}: a warning ended by hand (ms, 0 = not). {@code discord}: whether
     * it was posted to Discord. Pinned posts come first.
     */
    /**
     * One post. {@code starts}: visible from (0 = right away), {@code expires}: visible until (0 = for good);
     * {@code delivered}: chat previews / warning in game / Discord went out (a post planned for later gets them when
     * it becomes visible, see {@link NewsDelivery#tick}); {@code wantDiscord}: to Discord when it becomes visible.
     */
    public record Post(int id, String kind, Map<String, String> title, Map<String, String> text, String version,
                       List<Entry> entries, boolean discord, long created, long updated, long starts, long expires,
                       long ended, String author, boolean delivered, boolean wantDiscord) {

        public boolean visible(long now) {
            return (starts == 0 || starts <= now) && (expires == 0 || expires > now) && ended == 0;
        }

        /** When the players see it as written: the start, otherwise the creation. */
        public long published() {
            return starts > 0 ? Math.max(starts, created) : created;
        }

        public boolean alert() {
            return "alert".equals(kind);
        }

        /** Title or text in the language, otherwise in the other one. */
        public String in(Map<String, String> texts, String lang) {
            String own = texts.get(lang);
            if (own != null && !own.isBlank()) {
                return own;
            }
            return texts.values().stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse("");
        }

        /** What to call it in lists: the title, for a changelog without title its version. */
        public String heading(String lang) {
            String t = in(title, lang);
            return t.isEmpty() && version != null ? version : t;
        }

        /** A short plain preview: the text, for a changelog its entries one after another. */
        public String preview(String lang, int max) {
            String body;
            if ("changelog".equals(kind) && !entries.isEmpty()) {
                List<String> lines = new ArrayList<>();
                for (Entry e : entries) {
                    if (!e.heading()) {
                        lines.add(in(e.text(), lang));
                    }
                }
                body = String.join(" · ", lines);
            } else {
                body = in(text, lang).replace('\n', ' ');
            }
            return body.length() > max ? body.substring(0, Math.max(0, max - 1)).strip() + "…" : body;
        }
    }

    private final MRAdmin plugin;
    private final Map<Integer, Post> posts = new LinkedHashMap<>();
    private int nextId = 1;
    private boolean loaded;

    public News(MRAdmin plugin) {
        this.plugin = plugin;
    }

    /** All posts, newest first (by the time they are published). */
    public synchronized List<Post> all() {
        load();
        List<Post> list = new ArrayList<>(posts.values());
        list.sort(Comparator.comparingLong(Post::published).reversed());
        return list;
    }

    /** What players see right now (not expired, not ended), in the order of {@link #all()}. */
    public List<Post> visible() {
        long now = System.currentTimeMillis();
        return all().stream().filter(p -> p.visible(now)).toList();
    }

    /** Warnings that are on right now, newest first. */
    public List<Post> activeAlerts() {
        return visible().stream().filter(Post::alert).sorted(Comparator.comparingLong(Post::created).reversed()).toList();
    }

    public synchronized Post get(int id) {
        load();
        return posts.get(id);
    }

    /**
     * Creates ({@code id} 0) or changes a post; returns it as stored. Discord state, creation and whether it was
     * delivered stay; {@code wantDiscord} is only kept for a post not delivered yet.
     */
    public synchronized Post save(int id, String kind, Map<String, String> title, Map<String, String> text,
                                  String version, List<Entry> entries, long starts, long expires, String author,
                                  boolean wantDiscord) {
        load();
        long now = System.currentTimeMillis();
        Post old = id > 0 ? posts.get(id) : null;
        int postId = old != null ? old.id() : nextId++;
        boolean delivered = old != null && old.delivered();
        Post post = new Post(postId, kind, Map.copyOf(title), Map.copyOf(text), version == null ? "" : version,
                List.copyOf(entries), old != null && old.discord(), old != null ? old.created() : now, now, starts, expires,
                old != null ? old.ended() : 0, old != null ? old.author() : author, delivered, !delivered && wantDiscord);
        posts.put(postId, post);
        write();
        return post;
    }

    /** Notes that the post went to Discord. */
    public synchronized void markDiscord(int id) {
        load();
        Post p = posts.get(id);
        if (p != null && !p.discord()) {
            posts.put(id, copy(p, true, p.ended(), p.delivered()));
            write();
        }
    }

    /** Notes that chat previews / the warning went out (and Discord was tried if wanted). */
    public synchronized void markDelivered(int id) {
        load();
        Post p = posts.get(id);
        if (p != null && !p.delivered()) {
            posts.put(id, copy(p, p.discord(), p.ended(), true));
            write();
        }
    }

    /** Posts that are visible now but were not delivered yet (planned for later). */
    public synchronized List<Post> dueForDelivery(long now) {
        load();
        return posts.values().stream().filter(p -> !p.delivered() && p.visible(now)).toList();
    }

    /** Ends a warning now. @return whether there was an active one */
    public synchronized boolean end(int id) {
        load();
        Post p = posts.get(id);
        if (p == null || !p.alert() || p.ended() != 0) {
            return false;
        }
        posts.put(id, copy(p, p.discord(), System.currentTimeMillis(), p.delivered()));
        write();
        return true;
    }

    private static Post copy(Post p, boolean discord, long ended, boolean delivered) {
        return new Post(p.id(), p.kind(), p.title(), p.text(), p.version(), p.entries(), discord, p.created(),
                p.updated(), p.starts(), p.expires(), ended, p.author(), delivered, delivered ? false : p.wantDiscord());
    }

    public synchronized boolean delete(int id) {
        load();
        if (posts.remove(id) == null) {
            return false;
        }
        write();
        return true;
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        YamlConfiguration y = plugin.storage().loadYaml(FILE, plugin.getLogger());
        nextId = Math.max(1, y.getInt("next-id", 1));
        ConfigurationSection section = y.getConfigurationSection("posts");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection p = section.getConfigurationSection(key);
            int id;
            try {
                id = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }
            if (p == null) {
                continue;
            }
            String kind = KINDS.contains(p.getString("kind")) ? p.getString("kind") : "news";
            List<Entry> entries = new ArrayList<>();
            for (Map<?, ?> raw : p.getMapList("entries")) {
                Object type = raw.get("type");
                Map<String, String> text = new LinkedHashMap<>();
                for (String lang : LANGS) {
                    if (raw.get(lang) instanceof String s && !s.isBlank()) {
                        text.put(lang, s);
                    }
                }
                if (!text.isEmpty()) {
                    entries.add(new Entry(validType(type) ? (String) type : "changed", Map.copyOf(text)));
                }
            }
            posts.put(id, new Post(id, kind, texts(p, "title"), texts(p, "text"), p.getString("version", ""),
                    List.copyOf(entries), p.getBoolean("discord"), p.getLong("created"), p.getLong("updated"),
                    p.getLong("starts"), p.getLong("expires"), p.getLong("ended"), p.getString("author", ""),
                    p.getBoolean("delivered", true), p.getBoolean("want-discord")));
            nextId = Math.max(nextId, id + 1);
        }
    }

    private static Map<String, String> texts(ConfigurationSection p, String key) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String lang : LANGS) {
            String value = p.getString(key + "." + lang);
            if (value != null && !value.isBlank()) {
                result.put(lang, value);
            }
        }
        return result;
    }

    private void write() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MRadmin - news, changelogs and warnings for the players (managed in the web interface)."));
        y.set("next-id", nextId);
        for (Post p : posts.values()) {
            String base = "posts." + p.id() + ".";
            y.set(base + "kind", p.kind());
            p.title().forEach((lang, v) -> y.set(base + "title." + lang, v));
            p.text().forEach((lang, v) -> y.set(base + "text." + lang, v));
            if (!p.version().isEmpty()) {
                y.set(base + "version", p.version());
            }
            if (!p.entries().isEmpty()) {
                List<Map<String, String>> list = new ArrayList<>();
                for (Entry e : p.entries()) {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("type", e.type());
                    m.putAll(e.text());
                    list.add(m);
                }
                y.set(base + "entries", list);
            }
            y.set(base + "discord", p.discord());
            y.set(base + "created", p.created());
            y.set(base + "updated", p.updated());
            if (p.starts() > 0) {
                y.set(base + "starts", p.starts());
            }
            y.set(base + "expires", p.expires());
            if (!p.delivered()) {
                y.set(base + "delivered", false);
                y.set(base + "want-discord", p.wantDiscord());
            }
            y.set(base + "ended", p.ended());
            y.set(base + "author", p.author());
        }
        plugin.storage().saveYaml(FILE, y);
    }
}

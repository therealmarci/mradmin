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
 *   <li><b>changelog</b>: version, optional title, entries of a type (new, changed, fixed, removed)</li>
 *   <li><b>alert</b>: a critical warning (title + text), active until it runs out or is ended</li>
 * </ul>
 */
public final class News {

    public static final int MAX_TITLE = 120;
    public static final int MAX_TEXT = 4000;
    public static final int MAX_ENTRY = 300;
    public static final int MAX_ENTRIES = 60;
    public static final List<String> KINDS = List.of("news", "changelog", "alert");
    public static final List<String> ENTRY_TYPES = List.of("new", "changed", "fixed", "removed");
    public static final List<String> LANGS = List.of("de", "en");
    private static final String FILE = "news";

    /** One line of a changelog: its type and the text per language. */
    public record Entry(String type, Map<String, String> text) {
    }

    /**
     * One post. {@code title}/{@code text}: language code -> text. {@code expires}: hidden (or, for a warning, over)
     * from this time on (ms, 0 = never); {@code ended}: a warning ended by hand (ms, 0 = not). {@code discord}: whether
     * it was posted to Discord. Pinned posts come first.
     */
    public record Post(int id, String kind, Map<String, String> title, Map<String, String> text, String version,
                       List<Entry> entries, boolean pinned, boolean discord, long created, long updated, long expires,
                       long ended, String author) {

        public boolean visible(long now) {
            return (expires == 0 || expires > now) && ended == 0;
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
                    lines.add(in(e.text(), lang));
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

    /** All posts, pinned first, then newest first. */
    public synchronized List<Post> all() {
        load();
        List<Post> list = new ArrayList<>(posts.values());
        list.sort(Comparator.comparing(Post::pinned).reversed().thenComparing(Comparator.comparingLong(Post::created).reversed()));
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

    /** Creates ({@code id} 0) or changes a post; returns it as stored. Discord state and creation stay. */
    public synchronized Post save(int id, String kind, Map<String, String> title, Map<String, String> text,
                                  String version, List<Entry> entries, boolean pinned, long expires, String author) {
        load();
        long now = System.currentTimeMillis();
        Post old = id > 0 ? posts.get(id) : null;
        int postId = old != null ? old.id() : nextId++;
        Post post = new Post(postId, kind, Map.copyOf(title), Map.copyOf(text), version == null ? "" : version,
                List.copyOf(entries), pinned, old != null && old.discord(), old != null ? old.created() : now, now, expires,
                old != null ? old.ended() : 0, old != null ? old.author() : author);
        posts.put(postId, post);
        write();
        return post;
    }

    /** Notes that the post went to Discord. */
    public synchronized void markDiscord(int id) {
        load();
        Post p = posts.get(id);
        if (p != null && !p.discord()) {
            posts.put(id, copy(p, true, p.ended()));
            write();
        }
    }

    /** Ends a warning now. @return whether there was an active one */
    public synchronized boolean end(int id) {
        load();
        Post p = posts.get(id);
        if (p == null || !p.alert() || p.ended() != 0) {
            return false;
        }
        posts.put(id, copy(p, p.discord(), System.currentTimeMillis()));
        write();
        return true;
    }

    private static Post copy(Post p, boolean discord, long ended) {
        return new Post(p.id(), p.kind(), p.title(), p.text(), p.version(), p.entries(), p.pinned(), discord, p.created(),
                p.updated(), p.expires(), ended, p.author());
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
                    entries.add(new Entry(ENTRY_TYPES.contains(type) ? (String) type : "changed", Map.copyOf(text)));
                }
            }
            posts.put(id, new Post(id, kind, texts(p, "title"), texts(p, "text"), p.getString("version", ""),
                    List.copyOf(entries), p.getBoolean("pinned"), p.getBoolean("discord"), p.getLong("created"),
                    p.getLong("updated"), p.getLong("expires"), p.getLong("ended"), p.getString("author", "")));
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
            y.set(base + "pinned", p.pinned());
            y.set(base + "discord", p.discord());
            y.set(base + "created", p.created());
            y.set(base + "updated", p.updated());
            y.set(base + "expires", p.expires());
            y.set(base + "ended", p.ended());
            y.set(base + "author", p.author());
        }
        plugin.storage().saveYaml(FILE, y);
    }
}

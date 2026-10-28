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
 * Posts of the team for the players' own page (/web): news and changelogs, each with a title and text per language
 * (German, English - a missing language shows the other one). Written in the web interface ("News", mradmin.news),
 * shown in the slideshow of the player area. Kept in the storage file "news". Web threads only.
 */
public final class News {

    public static final int MAX_TITLE = 120;
    public static final int MAX_TEXT = 4000;
    public static final List<String> KINDS = List.of("news", "changelog");
    public static final List<String> LANGS = List.of("de", "en");
    private static final String FILE = "news";

    /**
     * One post. {@code title}/{@code text}: language code -> text. {@code expires}: hidden from this time on (ms,
     * 0 = never). Pinned posts come first.
     */
    public record Post(int id, String kind, Map<String, String> title, Map<String, String> text, boolean pinned,
                       long created, long updated, long expires, String author) {

        public boolean visible(long now) {
            return expires == 0 || expires > now;
        }

        /** Title or text in the language, otherwise in the other one. */
        public String in(Map<String, String> texts, String lang) {
            String own = texts.get(lang);
            if (own != null && !own.isBlank()) {
                return own;
            }
            return texts.values().stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse("");
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

    /** What players see right now (not expired), in the order of {@link #all()}. */
    public List<Post> visible() {
        long now = System.currentTimeMillis();
        return all().stream().filter(p -> p.visible(now)).toList();
    }

    public synchronized Post get(int id) {
        load();
        return posts.get(id);
    }

    /** Creates ({@code id} 0) or changes a post; returns it as stored. */
    public synchronized Post save(int id, String kind, Map<String, String> title, Map<String, String> text,
                                  boolean pinned, long expires, String author) {
        load();
        long now = System.currentTimeMillis();
        Post old = id > 0 ? posts.get(id) : null;
        int postId = old != null ? old.id() : nextId++;
        Post post = new Post(postId, kind, Map.copyOf(title), Map.copyOf(text), pinned,
                old != null ? old.created() : now, now, expires, old != null ? old.author() : author);
        posts.put(postId, post);
        write();
        return post;
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
            posts.put(id, new Post(id, kind, texts(p, "title"), texts(p, "text"), p.getBoolean("pinned"),
                    p.getLong("created"), p.getLong("updated"), p.getLong("expires"), p.getString("author", "")));
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
        y.options().setHeader(List.of("MRadmin - news and changelogs for the players' own page (managed in the web interface)."));
        y.set("next-id", nextId);
        for (Post p : posts.values()) {
            String base = "posts." + p.id() + ".";
            y.set(base + "kind", p.kind());
            p.title().forEach((lang, v) -> y.set(base + "title." + lang, v));
            p.text().forEach((lang, v) -> y.set(base + "text." + lang, v));
            y.set(base + "pinned", p.pinned());
            y.set(base + "created", p.created());
            y.set(base + "updated", p.updated());
            y.set(base + "expires", p.expires());
            y.set(base + "author", p.author());
        }
        plugin.storage().saveYaml(FILE, y);
    }
}

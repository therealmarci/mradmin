package at.marcelr.mradmin.announce;

import at.marcelr.mradmin.MRAdmin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Announcement templates made by the team in the web interface (page "Communication"), used like the presets of the
 * language file: in the web, in the announcement menu in game and with {@code /mradmin announce <name>}. Each one has
 * its texts per language (title, subtitle, chat, action bar, Discord), whether it goes to Discord, the times of the
 * title, a sound and who gets it. Kept in the storage file "announce-templates". Thread-safe.
 */
public final class AnnounceTemplates {

    /** Who gets it, from everyone to the admins only; every group gets what is meant for a lower one. */
    public static final List<String> AUDIENCES = List.of("player", "bot", "support", "staff", "admin");
    public static final List<String> PARTS = List.of("title", "subtitle", "chat", "actionbar", "discord");
    public static final List<String> LANGS = List.of("de", "en");
    public static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    public static final int MAX_TEXT = 300;
    /** Sound: the one from the config / none. Anything else is a sound key. */
    public static final String SOUND_DEFAULT = "default";
    public static final String SOUND_NONE = "none";
    private static final String FILE = "announce-templates";

    /**
     * One template. {@code texts}: part ({@link #PARTS}) -> language -> MiniMessage text (Discord: Markdown).
     * Times in seconds.
     */
    public record Template(String name, Map<String, Map<String, String>> texts, boolean discord, double fadeIn,
                           double stay, double fadeOut, String sound, float volume, float pitch, String audience,
                           long updated, String author) {

        /** A part in the language, otherwise in the other one; "" if there is none. */
        public String text(String part, String lang) {
            Map<String, String> byLang = texts.getOrDefault(part, Map.of());
            String own = byLang.get(lang);
            if (own != null && !own.isBlank()) {
                return own;
            }
            return byLang.values().stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse("");
        }

        public boolean has(String part) {
            return texts.getOrDefault(part, Map.of()).values().stream().anyMatch(v -> v != null && !v.isBlank());
        }

        /** Position of the audience: 0 everyone ... 4 admins only. */
        public int level() {
            return Math.max(0, AUDIENCES.indexOf(audience));
        }
    }

    private final MRAdmin plugin;
    private final Map<String, Template> templates = new LinkedHashMap<>();
    private boolean loaded;

    public AnnounceTemplates(MRAdmin plugin) {
        this.plugin = plugin;
    }

    /** All templates by name. */
    public synchronized List<Template> all() {
        load();
        List<Template> list = new ArrayList<>(templates.values());
        list.sort(Comparator.comparing(Template::name));
        return list;
    }

    /** The template with this name (case-insensitive), or null. */
    public synchronized Template get(String name) {
        load();
        return name == null ? null : templates.get(name.toLowerCase(Locale.ROOT));
    }

    /** Creates or replaces a template; {@code original} is its name before (renamed), null for a new one. */
    public synchronized void save(Template template, String original) {
        load();
        if (original != null && !original.equals(template.name())) {
            templates.remove(original);
        }
        templates.put(template.name(), template);
        write();
    }

    public synchronized boolean delete(String name) {
        load();
        if (templates.remove(name) == null) {
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
        ConfigurationSection section = y.getConfigurationSection("templates");
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(name);
            if (s == null || !NAME.matcher(name).matches()) {
                continue;
            }
            Map<String, Map<String, String>> texts = new LinkedHashMap<>();
            for (String part : PARTS) {
                Map<String, String> byLang = new LinkedHashMap<>();
                for (String lang : LANGS) {
                    String value = s.getString("texts." + part + "." + lang);
                    if (value != null && !value.isBlank()) {
                        byLang.put(lang, value);
                    }
                }
                if (!byLang.isEmpty()) {
                    texts.put(part, Map.copyOf(byLang));
                }
            }
            String audience = s.getString("audience", "player");
            templates.put(name, new Template(name, Map.copyOf(texts), s.getBoolean("discord"),
                    s.getDouble("fade-in", 0.5), s.getDouble("stay", 3.5), s.getDouble("fade-out", 1),
                    s.getString("sound", SOUND_DEFAULT), (float) s.getDouble("volume", 1), (float) s.getDouble("pitch", 1),
                    AUDIENCES.contains(audience) ? audience : "player", s.getLong("updated"), s.getString("author", "")));
        }
    }

    private void write() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MRadmin - announcement templates (managed in the web interface, page Communication)."));
        for (Template t : templates.values()) {
            String base = "templates." + t.name() + ".";
            t.texts().forEach((part, byLang) -> byLang.forEach((lang, value) -> y.set(base + "texts." + part + "." + lang, value)));
            y.set(base + "discord", t.discord());
            y.set(base + "fade-in", t.fadeIn());
            y.set(base + "stay", t.stay());
            y.set(base + "fade-out", t.fadeOut());
            y.set(base + "sound", t.sound());
            y.set(base + "volume", (double) t.volume());
            y.set(base + "pitch", (double) t.pitch());
            y.set(base + "audience", t.audience());
            y.set(base + "updated", t.updated());
            y.set(base + "author", t.author());
        }
        plugin.storage().saveYaml(FILE, y);
    }
}

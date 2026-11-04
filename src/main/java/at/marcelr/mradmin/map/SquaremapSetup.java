package at.marcelr.mradmin.map;

import at.marcelr.mradmin.MRAdmin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps the recommended settings in squaremap's config.yml up to date - checked on every start. MRadmin remembers
 * which values it wrote (squaremap-setup.yml): a value that is missing or still the one MRadmin wrote gets the
 * current recommendation, a value the server owner changed stays. Values MRadmin does not recommend any more are
 * removed again if nobody changed them. Only touches the file; needs no squaremap classes.
 * <p>
 * Until MRadmin 1.1 MRchunks did this - its squaremap-setup.yml is taken over, so its values count as written.
 */
final class SquaremapSetup {

    /** Format of squaremap-setup.yml; 1 = only "applied-version" (written values unknown, MRchunks 0.x). */
    static final int VERSION = 2;

    /** The main dimensions get a readable name - other worlds are left alone. */
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";

    private final MRAdmin plugin;
    private final File squaremapConfig;
    private final File marker;

    SquaremapSetup(MRAdmin plugin) {
        this.plugin = plugin;
        this.squaremapConfig = new File(plugin.getDataFolder().getParentFile(), "squaremap/config.yml");
        this.marker = new File(plugin.getDataFolder(), "squaremap-setup.yml");
    }

    /**
     * One recommended value and how it is described in the log and to the admin. {@code stock}: squaremap's own
     * default - a key MRadmin starts managing later still holds it on servers set up before, that is no change by hand.
     */
    private record Want(Object value, Change label, Object stock) {
        Want(Object value, Change label) {
            this(value, label, null);
        }
    }

    /** squaremap names worlds by their key and replaces dots, which it splits paths on. */
    private static String worldPath(World world) {
        return "world-settings." + world.getKey().asString().replace(".", "____dot____") + ".map.";
    }

    /** What MRadmin recommends right now, by path in squaremap's config. */
    private Map<String, Want> wanted(MapSettings settings) {
        Map<String, Want> want = new LinkedHashMap<>();
        want.put("world-settings.default.map.render.background.interval-seconds",
                new Want(5, new Change("map-setup-change-interval")));
        want.put("world-settings.default.player-tracker.enabled", new Want(false, new Change("map-setup-change-tracker")));
        // Only MRadmin shows the map (player page) - squaremap's own web page and server stay off.
        want.put("settings.internal-webserver.enabled", new Want(false, new Change("map-setup-change-webserver"), true));
        for (World world : Bukkit.getWorlds()) {
            String base = worldPath(world);
            if (settings.hideModuleWorlds() && plugin.modules().worldOwner(world) != null) {
                // e.g. MRpvp's arena world - nothing to see for visitors.
                want.put(base + "enabled", new Want(false, new Change("map-setup-change-hidden", "world", world.getName())));
                continue;
            }
            String name = switch (world.getKey().asString()) {
                case OVERWORLD -> dimensionName("map-dimension-overworld");
                case NETHER -> dimensionName("map-dimension-nether");
                case END -> dimensionName("map-dimension-end");
                default -> null;
            };
            if (name != null && !name.isBlank()) {
                want.put(base + "display-name",
                        new Want(name, new Change("map-setup-change-name", "world", world.getName(), "name", name)));
            }
            if (world.getEnvironment() == World.Environment.NETHER) {
                // Without this the nether map only shows the bedrock roof - also in further nether worlds.
                want.put(base + "iterate-up", new Want(true, new Change("map-setup-change-nether", "world", world.getName())));
                want.put(base + "max-height", new Want(127, null));
            }
        }
        return want;
    }

    private String dimensionName(String key) {
        return plugin.messages().server(() -> plugin.messages().optional(key));
    }

    /**
     * What version 1 (MRchunks 0.x) wrote once, without remembering it: the same defaults and a name for every world
     * by its environment - also for other worlds of that kind, which is why those names are taken back now.
     */
    private Map<String, Object> legacyWritten() {
        Map<String, Object> written = new LinkedHashMap<>();
        written.put("world-settings.default.map.render.background.interval-seconds", 5);
        written.put("world-settings.default.player-tracker.enabled", false);
        written.put("settings.ui.sidebar.pinned", "pinned");
        for (World world : Bukkit.getWorlds()) {
            String base = "world-settings." + world.getKey().asString() + ".map.";
            String name = switch (world.getEnvironment()) {
                case NORMAL -> dimensionName("map-dimension-overworld");
                case NETHER -> dimensionName("map-dimension-nether");
                case THE_END -> dimensionName("map-dimension-end");
                default -> null;
            };
            if (name != null && !name.isBlank()) {
                written.put(base + "display-name", name);
            }
            if (world.getEnvironment() == World.Environment.NETHER) {
                written.put(base + "iterate-up", true);
                written.put(base + "max-height", 127);
            }
        }
        return written;
    }

    /** Takes over MRchunks' memory of what it wrote (once, if MRadmin has none yet). */
    private void takeOverFromMrchunks() throws IOException {
        File old = new File(plugin.getDataFolder().getParentFile(), "MRchunks/squaremap-setup.yml");
        if (!marker.exists() && old.isFile()) {
            Files.createDirectories(marker.getParentFile().toPath());
            Files.copy(old.toPath(), marker.toPath());
            plugin.getLogger().info("Took over MRchunks' squaremap setup (" + old.getPath() + ").");
        }
    }

    private Map<String, Object> written() {
        YamlConfiguration done = YamlConfiguration.loadConfiguration(marker);
        int version = done.getInt("applied-version", 0);
        if (version == 0) {
            return Map.of();
        }
        if (version == 1) {
            return legacyWritten();
        }
        Map<String, Object> written = new LinkedHashMap<>();
        // A list instead of keys: squaremap paths contain dots and colons.
        for (Map<?, ?> entry : done.getMapList("written")) {
            if (entry.get("path") instanceof String path) {
                written.put(path, entry.get("value"));
            }
        }
        return written;
    }

    /**
     * Brings squaremap's config up to date and remembers what was written.
     * @param force also overwrite values the server owner changed (/mradmin map setup)
     * @return the changes made (for the log / the admin), empty if nothing changed or squaremap has no config yet
     */
    List<Change> apply(MapSettings settings, boolean force) throws IOException {
        List<Change> changes = new ArrayList<>();
        if (!squaremapConfig.exists()) {
            return changes;
        }
        takeOverFromMrchunks();
        YamlConfiguration config = new YamlConfiguration();
        config.options().parseComments(true);
        config.options().width(10_000);
        try {
            config.load(squaremapConfig);
        } catch (InvalidConfigurationException e) {
            throw new IOException("squaremap/config.yml is invalid: " + e.getMessage(), e);
        }
        Map<String, Object> before = written();
        // Never set up before: squaremap's own defaults are in the file - those are not changes by hand.
        boolean first = YamlConfiguration.loadConfiguration(marker).getInt("applied-version", 0) == 0;
        Map<String, Object> now = new LinkedHashMap<>();
        boolean dirty = false;

        for (Map.Entry<String, Want> e : wanted(settings).entrySet()) {
            String path = e.getKey();
            Want want = e.getValue();
            Object current = config.get(path);
            if (same(want.value(), current)) {
                now.put(path, want.value());
                continue;
            }
            boolean ours = current == null || (before.containsKey(path) && same(before.get(path), current))
                    || (!before.containsKey(path) && want.stock() != null && same(want.stock(), current));
            if (!ours && !force && !first) {
                continue; // changed by hand - stays
            }
            config.set(path, want.value());
            now.put(path, want.value());
            dirty = true;
            if (want.label() != null) {
                changes.add(want.label());
            }
        }
        // No longer recommended (e.g. a name version 1 gave every world): remove it, unless it was changed by hand.
        for (Map.Entry<String, Object> e : before.entrySet()) {
            String path = e.getKey();
            if (!now.containsKey(path) && config.get(path) != null && same(e.getValue(), config.get(path))) {
                config.set(path, null);
                dirty = true;
                changes.add(new Change("map-setup-change-removed", "setting", path.replace("world-settings.", "")));
            }
        }

        if (dirty) {
            config.save(squaremapConfig);
        }
        YamlConfiguration done = new YamlConfiguration();
        done.options().setHeader(List.of("What MRadmin wrote into squaremap/config.yml - values changed there by hand stay."));
        done.set("applied-version", VERSION);
        done.set("applied-at", System.currentTimeMillis());
        List<Map<String, Object>> list = new ArrayList<>();
        now.forEach((path, value) -> list.add(Map.of("path", path, "value", value)));
        done.set("written", list);
        done.save(marker);
        return changes;
    }

    /** YAML numbers come back as Integer, Long or Double - compare by text. */
    private static boolean same(Object a, Object b) {
        return Objects.equals(a, b) || (a != null && b != null && String.valueOf(a).equals(String.valueOf(b)));
    }

    /** One applied setting: language key (map-setup-change-*) and its placeholders as name/value pairs. */
    record Change(String key, Object... placeholders) {
    }
}

package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.MRAdmin;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The game's own statistics of every player (also offline): Minecraft keeps them in
 * {@code <world>/players/stats/<uuid>.json} (before Minecraft 26: {@code <world>/stats}).
 * Read in the background (never on the server thread) and kept for 10 minutes. Which rankings exist is set in
 * {@code player-web.rankings}.
 */
public final class VanillaStats {

    private static final long CACHE_MILLIS = 10 * 60_000L;
    private static final double CM_PER_KM = 100_000.0;
    private static final double TICKS_PER_HOUR = 20 * 3600.0;

    /** A ranking of the game's statistics: how to count it and how to show the value. */
    public enum Kind {
        PLAY_TIME("play-time"),
        BLOCKS_MINED("blocks-mined"),
        MOB_KILLS("mob-kills"),
        XP_LEVEL("xp-level"),
        DEATHS("deaths"),
        DISTANCE("distance"),
        FISH_CAUGHT("fish-caught"),
        JUMPS("jumps"),
        ITEMS_CRAFTED("items-crafted");

        public final String key;

        Kind(String key) {
            this.key = key;
        }

        public static Kind byKey(String key) {
            for (Kind kind : values()) {
                if (kind.key.equalsIgnoreCase(key.trim())) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** One player's value of a ranking. */
    public record Entry(UUID player, String name, double value) {
    }

    private final MRAdmin plugin;
    private volatile Map<Kind, List<Entry>> cache = Map.of();
    private volatile long cachedAt;
    private boolean logged;

    public VanillaStats(MRAdmin plugin) {
        this.plugin = plugin;
    }

    /** All players with this statistic, best first (cached, read again after 10 minutes). Web threads only. */
    public synchronized List<Entry> ranking(Kind kind) {
        if (System.currentTimeMillis() - cachedAt > CACHE_MILLIS) {
            cache = read();
            cachedAt = System.currentTimeMillis();
        }
        return cache.getOrDefault(kind, List.of());
    }

    /** How many players there are statistics of (the ones with a name). Web threads only. */
    public synchronized int players() {
        ranking(Kind.PLAY_TIME);
        return cache.values().stream().mapToInt(List::size).max().orElse(0);
    }

    /** One player's value of a statistic (0 if they have none), from the same cache. Web threads only. */
    public synchronized double of(UUID player, Kind kind) {
        for (Entry e : ranking(kind)) {
            if (e.player().equals(player)) {
                return e.value();
            }
        }
        return 0;
    }

    private Map<Kind, List<Entry>> read() {
        Map<Kind, List<Entry>> result = new HashMap<>();
        for (Kind kind : Kind.values()) {
            result.put(kind, new ArrayList<>());
        }
        // Minecraft 26: <level>/players/stats (the dimensions sit in <level>/dimensions/...), before: <level>/stats.
        File level = levelFolder();
        File folder = new File(level, "players/stats");
        if (!folder.isDirectory()) {
            folder = new File(level, "stats");
        }
        File playerData = new File(level, "players/data");
        if (!playerData.isDirectory()) {
            playerData = new File(level, "playerdata");
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (!logged) {
            logged = true;
            plugin.getLogger().info("Player statistics for the web interface: " + folder.getPath() + " ("
                    + (files == null ? 0 : files.length) + " players).");
        }
        if (files == null) {
            return result;
        }
        for (File file : files) {
            UUID player;
            try {
                player = UUID.fromString(file.getName().substring(0, file.getName().length() - 5));
            } catch (IllegalArgumentException e) {
                continue;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
            if (offline.getName() == null) {
                continue; // never really on this server (or a dummy) - no name to show
            }
            try {
                JsonObject stats = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("stats");
                if (stats == null) {
                    continue;
                }
                for (Kind kind : Kind.values()) {
                    double value = kind == Kind.XP_LEVEL ? PlayerDataLevel.read(new File(playerData, player + ".dat"))
                            : value(stats, kind);
                    if (value > 0) {
                        result.get(kind).add(new Entry(player, offline.getName(), value));
                    }
                }
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().fine("Could not read the statistics " + file.getName() + ": " + e.getMessage());
            }
        }
        result.values().forEach(list -> list.sort(Comparator.comparingDouble(Entry::value).reversed()));
        return result;
    }

    /**
     * The folder of the main world (the one with level.dat): since Minecraft 26 the world folder the server reports is
     * the dimension's (<level>/dimensions/minecraft/overworld), the players' files sit in the level folder above it.
     */
    private static File levelFolder() {
        File world = Bukkit.getWorlds().getFirst().getWorldFolder().getAbsoluteFile();
        for (File dir = world; dir != null; dir = dir.getParentFile()) {
            if (new File(dir, "level.dat").isFile()) {
                return dir;
            }
        }
        return world;
    }

    private static double value(JsonObject stats, Kind kind) {
        return switch (kind) {
            case PLAY_TIME -> custom(stats, "minecraft:play_time") / TICKS_PER_HOUR;
            case BLOCKS_MINED -> sum(stats.getAsJsonObject("minecraft:mined"));
            case MOB_KILLS -> custom(stats, "minecraft:mob_kills");
            case XP_LEVEL -> 0; // from the player data, see read()
            case DEATHS -> custom(stats, "minecraft:deaths");
            case DISTANCE -> (custom(stats, "minecraft:walk_one_cm") + custom(stats, "minecraft:sprint_one_cm")
                    + custom(stats, "minecraft:crouch_one_cm") + custom(stats, "minecraft:swim_one_cm")
                    + custom(stats, "minecraft:walk_under_water_one_cm") + custom(stats, "minecraft:walk_on_water_one_cm")
                    + custom(stats, "minecraft:aviate_one_cm") + custom(stats, "minecraft:horse_one_cm")
                    + custom(stats, "minecraft:boat_one_cm") + custom(stats, "minecraft:minecart_one_cm")) / CM_PER_KM;
            case FISH_CAUGHT -> custom(stats, "minecraft:fish_caught");
            case JUMPS -> custom(stats, "minecraft:jump");
            case ITEMS_CRAFTED -> sum(stats.getAsJsonObject("minecraft:crafted"));
        };
    }

    private static double custom(JsonObject stats, String key) {
        JsonObject custom = stats.getAsJsonObject("minecraft:custom");
        JsonElement value = custom == null ? null : custom.get(key);
        return value == null ? 0 : value.getAsDouble();
    }

    private static double sum(JsonObject group) {
        double sum = 0;
        if (group != null) {
            for (Map.Entry<String, JsonElement> e : group.entrySet()) {
                sum += e.getValue().getAsDouble();
            }
        }
        return sum;
    }

    /** The value as shown: hours and km with one decimal, everything else as a whole number. */
    public static String format(Kind kind, double value, String lang) {
        java.util.Locale locale = "de".equals(lang) ? java.util.Locale.GERMANY : java.util.Locale.UK;
        return switch (kind) {
            case PLAY_TIME -> String.format(locale, "%,.1f h", value);
            case DISTANCE -> String.format(locale, "%,.1f km", value);
            default -> String.format(locale, "%,d", Math.round(value));
        };
    }
}

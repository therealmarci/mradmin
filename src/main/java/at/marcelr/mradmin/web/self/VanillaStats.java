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
        ITEMS_CRAFTED("items-crafted"),
        BLOCKS_PLACED("blocks-placed"),
        TOTEMS("totems"),
        TIMES_SLEPT("times-slept"),
        DISTANCE_SWUM("distance-swum"),
        DISTANCE_FLOWN("distance-flown");

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

    /** An online player's values, read right from the server. */
    public record Live(String name, Map<Kind, Double> values) {
    }

    /** Online players' values (see {@link #live(Map)}) - they replace the saved ones, which may be minutes old. */
    private volatile Map<UUID, Live> live = Map.of();

    /**
     * All players with this statistic, best first: the saved files (cached, read again after 10 minutes), online players
     * with their current values. Web threads only.
     */
    public synchronized List<Entry> ranking(Kind kind) {
        if (System.currentTimeMillis() - cachedAt > CACHE_MILLIS) {
            cache = read();
            cachedAt = System.currentTimeMillis();
        }
        List<Entry> saved = cache.getOrDefault(kind, List.of());
        Map<UUID, Live> now = live;
        if (now.isEmpty()) {
            return saved;
        }
        List<Entry> merged = new ArrayList<>();
        for (Entry e : saved) {
            if (!now.containsKey(e.player())) {
                merged.add(e);
            }
        }
        now.forEach((uuid, l) -> {
            double value = l.values().getOrDefault(kind, 0.0);
            if (value > 0) {
                merged.add(new Entry(uuid, l.name(), value));
            }
        });
        merged.sort(Comparator.comparingDouble(Entry::value).reversed());
        return merged;
    }

    /** Sets the current values of the online players (from {@link #readLive()}, before answering a request). */
    public void live(Map<UUID, Live> values) {
        live = Map.copyOf(values);
    }

    /**
     * The current values of online players ({@code only} null = all, otherwise just that one), straight from the
     * server - Minecraft only writes them to the files when it saves. Main thread only.
     */
    public static Map<UUID, Live> readLive(UUID only) {
        Map<UUID, Live> result = new HashMap<>();
        for (org.bukkit.entity.Player p : only == null ? List.copyOf(Bukkit.getOnlinePlayers())
                : java.util.Optional.ofNullable(Bukkit.getPlayer(only)).map(List::of).orElse(List.of())) {
            Map<Kind, Double> v = new java.util.EnumMap<>(Kind.class);
            v.put(Kind.PLAY_TIME, p.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / TICKS_PER_HOUR);
            v.put(Kind.BLOCKS_MINED, perMaterial(p, org.bukkit.Statistic.MINE_BLOCK, true));
            v.put(Kind.MOB_KILLS, (double) p.getStatistic(org.bukkit.Statistic.MOB_KILLS));
            v.put(Kind.XP_LEVEL, (double) p.getLevel());
            v.put(Kind.DEATHS, (double) p.getStatistic(org.bukkit.Statistic.DEATHS));
            double cm = 0;
            for (org.bukkit.Statistic s : List.of(org.bukkit.Statistic.WALK_ONE_CM, org.bukkit.Statistic.SPRINT_ONE_CM,
                    org.bukkit.Statistic.CROUCH_ONE_CM, org.bukkit.Statistic.SWIM_ONE_CM,
                    org.bukkit.Statistic.WALK_UNDER_WATER_ONE_CM, org.bukkit.Statistic.WALK_ON_WATER_ONE_CM,
                    org.bukkit.Statistic.AVIATE_ONE_CM, org.bukkit.Statistic.HORSE_ONE_CM,
                    org.bukkit.Statistic.BOAT_ONE_CM, org.bukkit.Statistic.MINECART_ONE_CM)) {
                cm += p.getStatistic(s);
            }
            v.put(Kind.DISTANCE, cm / CM_PER_KM);
            v.put(Kind.FISH_CAUGHT, (double) p.getStatistic(org.bukkit.Statistic.FISH_CAUGHT));
            v.put(Kind.JUMPS, (double) p.getStatistic(org.bukkit.Statistic.JUMP));
            v.put(Kind.ITEMS_CRAFTED, perMaterial(p, org.bukkit.Statistic.CRAFT_ITEM, false));
            v.put(Kind.BLOCKS_PLACED, placed(p));
            v.put(Kind.TOTEMS, (double) p.getStatistic(org.bukkit.Statistic.USE_ITEM, org.bukkit.Material.TOTEM_OF_UNDYING));
            v.put(Kind.TIMES_SLEPT, (double) p.getStatistic(org.bukkit.Statistic.SLEEP_IN_BED));
            v.put(Kind.DISTANCE_SWUM, p.getStatistic(org.bukkit.Statistic.SWIM_ONE_CM) / CM_PER_KM);
            v.put(Kind.DISTANCE_FLOWN, (p.getStatistic(org.bukkit.Statistic.AVIATE_ONE_CM)
                    + p.getStatistic(org.bukkit.Statistic.FLY_ONE_CM)) / CM_PER_KM);
            result.put(p.getUniqueId(), new Live(p.getName(), v));
        }
        return result;
    }


    /** Placed blocks of an online player: "used" of every item that is a block (that is what placing counts). */
    private static double placed(org.bukkit.entity.Player p) {
        double sum = 0;
        for (org.bukkit.Material m : org.bukkit.Material.values()) {
            if (!m.isBlock() || !m.isItem()) {
                continue;
            }
            try {
                sum += p.getStatistic(org.bukkit.Statistic.USE_ITEM, m);
            } catch (IllegalArgumentException ignored) {
                // not counted for this one
            }
        }
        return sum;
    }

    /** Placed blocks from the file: the "used" entries of items that are blocks. */
    private static double placed(JsonObject used) {
        double sum = 0;
        if (used != null) {
            for (Map.Entry<String, JsonElement> e : used.entrySet()) {
                org.bukkit.Material m = org.bukkit.Material.matchMaterial(e.getKey());
                if (m != null && m.isBlock()) {
                    sum += e.getValue().getAsDouble();
                }
            }
        }
        return sum;
    }

    private static double number(JsonObject group, String key) {
        JsonElement value = group == null ? null : group.get(key);
        return value == null ? 0 : value.getAsDouble();
    }
    /** A statistic per block (mined) or per item (crafted) added up over all of them. */
    private static double perMaterial(org.bukkit.entity.Player p, org.bukkit.Statistic statistic, boolean blocks) {
        double sum = 0;
        for (org.bukkit.Material m : org.bukkit.Material.values()) {
            if (blocks ? !m.isBlock() : !m.isItem()) {
                continue;
            }
            try {
                sum += p.getStatistic(statistic, m);
            } catch (IllegalArgumentException ignored) {
                // not counted for this one (e.g. air)
            }
        }
        return sum;
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
            case BLOCKS_PLACED -> placed(stats.getAsJsonObject("minecraft:used"));
            case TOTEMS -> number(stats.getAsJsonObject("minecraft:used"), "minecraft:totem_of_undying");
            case TIMES_SLEPT -> custom(stats, "minecraft:sleep_in_bed");
            case DISTANCE_SWUM -> custom(stats, "minecraft:swim_one_cm") / CM_PER_KM;
            case DISTANCE_FLOWN -> (custom(stats, "minecraft:aviate_one_cm") + custom(stats, "minecraft:fly_one_cm")) / CM_PER_KM;
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
            case DISTANCE, DISTANCE_SWUM, DISTANCE_FLOWN -> String.format(locale, "%,.1f km", value);
            default -> String.format(locale, "%,d", Math.round(value));
        };
    }
}

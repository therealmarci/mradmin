package at.marcelr.mradmin.ticket;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.health.HealthMonitor;
import at.marcelr.mradmin.health.HotChunks;
import at.marcelr.mradmin.log.EventLog;
import at.marcelr.mradmin.web.ServerLogFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

/**
 * Log snapshots of tickets (since 1.7): when a ticket of a category in tickets.attach-log is created, everything that
 * helps to understand it is saved with it for good - the last minutes of the server log and of the event log, the
 * performance per second, server data (versions, plugins, worlds, busy chunks) and the surroundings of the player
 * (position, state, entities and block entities nearby). The team sees it in the web interface; MRtechnology gets it
 * only sealed ({@link LogSeal}), as a file or through a share link that serves the sealed file.
 */
public final class TicketLogs {

    public static final String FORMAT = "mrtechnology-ticket-log";
    /** The viewer on the MRtechnology website that opens sealed logs (and the only origin allowed to fetch them). */
    public static final String VIEWER = "https://mrtechnology.marcelr.at/log/";
    public static final String VIEWER_ORIGIN = "https://mrtechnology.marcelr.at";
    private static final int MAX_CONSOLE = 10_000;
    private static final int MAX_EVENTS = 5_000;
    private static final int MAX_LINE = 2_000;
    private static final String SHARES = "ticket-log-shares";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeSpecialFloatingPointValues().create();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MRAdmin plugin;

    private final LogArea area;

    TicketLogs(MRAdmin plugin) {
        this.plugin = plugin;
        this.area = new LogArea(plugin);
    }

    /** Records despawns, deaths and player positions for the area part (registered as a listener). */
    public LogArea area() {
        return area;
    }

    private Settings.AttachLog settings() {
        return plugin.settings().tickets().attachLog();
    }

    /**
     * Takes the snapshot of a new ticket if its category wants one. Server thread: the world parts are read right
     * away, the files (server log, event log) and the saving happen in the background.
     */
    void capture(Ticket ticket, String categoryName) {
        capture(ticket, categoryName, null);
    }

    /**
     * @param module a part delivered by the plugin that opened the ticket (e.g. MRchunks' chunk loader) - kept only
     *               locally, never in the sealed file for MRtechnology; with it the ticket gets a log in any category
     */
    void capture(Ticket ticket, String categoryName, Map<String, Object> module) {
        Settings.AttachLog s = settings();
        if (!s.enabled() || (!s.wants(ticket.category()) && module == null)) {
            return;
        }
        long now = System.currentTimeMillis();
        long since = now - s.minutes() * 60_000L;
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("format", FORMAT);
        log.put("version", 1);
        log.put("time", now);
        log.put("from", since);
        Map<String, Object> about = new LinkedHashMap<>();
        about.put("id", ticket.id());
        about.put("category", ticket.category());
        about.put("categoryName", categoryName);
        about.put("summary", plugin.tickets().summary(ticket));
        about.put("creator", ticket.creatorName());
        about.put("creatorUuid", ticket.creator().toString());
        about.put("server", plugin.branding().name());
        about.put("place", Map.of("world", ticket.world(), "x", ticket.blockX(), "y", ticket.blockY(), "z", ticket.blockZ()));
        log.put("ticket", about);
        try {
            log.put("server", server());
            Player creator = Bukkit.getPlayer(ticket.creator());
            if (creator != null) {
                log.put("player", surroundings(creator, s.radius()));
            }
            Player target = ticket.target() == null ? null : Bukkit.getPlayer(ticket.target());
            if (target != null) {
                log.put("target", surroundings(target, s.radius()));
            }
            log.put("hotChunks", hotChunks());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Ticket #" + ticket.id() + ": could not read everything for the log: " + e);
        }
        if (module != null) {
            log.put("module", module);
        }
        // the area around the creator, otherwise around the place of the ticket
        CompletableFuture<Map<String, Object>> around = null;
        try {
            Player creator = Bukkit.getPlayer(ticket.creator());
            World world = Bukkit.getWorld(ticket.world());
            Location place = creator != null ? creator.getLocation()
                    : world == null ? null : new Location(world, ticket.blockX(), ticket.blockY(), ticket.blockZ());
            if (place != null) {
                around = area.capture(place, creator, since);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Ticket #" + ticket.id() + ": could not read the area for the log: " + e);
        }
        CompletableFuture<Map<String, Object>> areaPart = around;
        List<HealthMonitor.Point> points = plugin.health().range(now - since, s.minutes() * 60);
        int id = ticket.id();
        boolean links = plugin.getConfig().getBoolean("web.console.hide-links", true);
        boolean ips = plugin.getConfig().getBoolean("web.console.hide-ips", true);
        Thread.ofVirtual().name("MRadmin-TicketLog").start(() -> {
            try {
                log.put("health", health(points));
                log.put("console", console(since, links, ips));
                log.put("events", events(since));
                if (areaPart != null) {
                    try {
                        log.put("area", areaPart.get(30, java.util.concurrent.TimeUnit.SECONDS));
                    } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException e) {
                        plugin.getLogger().warning("Ticket #" + id + ": the area for the log took too long: " + e);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                plugin.storage().saveTicketLog(id, gzip(GSON.toJson(log)));
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().warning("Could not save the log of ticket #" + id + ": " + e);
            }
        });
    }

    /** The stored snapshot as gzipped JSON, or null. Blocking - not on the server thread. */
    public byte[] load(int ticketId) {
        return plugin.storage().loadTicketLog(ticketId);
    }

    // ------------------------------------------------------------------ parts

    private Map<String, Object> server() {
        Map<String, Object> s = new LinkedHashMap<>();
        var runtime = ManagementFactory.getRuntimeMXBean();
        s.put("software", Bukkit.getName() + " " + Bukkit.getVersion());
        s.put("minecraft", Bukkit.getMinecraftVersion());
        s.put("mradmin", plugin.getPluginMeta().getVersion());
        s.put("java", System.getProperty("java.vendor") + " " + System.getProperty("java.version"));
        s.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        s.put("cores", Runtime.getRuntime().availableProcessors());
        s.put("memoryMax", Runtime.getRuntime().maxMemory() / 1_048_576);
        s.put("uptime", runtime.getUptime());
        s.put("jvmFlags", runtime.getInputArguments().stream().filter(a -> a.startsWith("-X") || a.startsWith("-XX")).toList());
        s.put("online", Bukkit.getOnlinePlayers().size());
        s.put("maxPlayers", Bukkit.getMaxPlayers());
        s.put("onlineMode", Bukkit.getOnlineMode());
        s.put("viewDistance", Bukkit.getViewDistance());
        s.put("simulationDistance", Bukkit.getSimulationDistance());
        double[] tps = Bukkit.getTPS();
        s.put("tps", List.of(round(tps[0]), round(tps[1]), round(tps[2])));
        s.put("mspt", round(Bukkit.getAverageTickTime()));
        s.put("storage", plugin.storage().type());
        List<Map<String, Object>> worlds = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", w.getName());
            m.put("environment", w.getEnvironment().name().toLowerCase(Locale.ROOT));
            m.put("players", w.getPlayers().size());
            m.put("chunks", w.getLoadedChunks().length);
            m.put("entities", w.getEntityCount());
            m.put("tiles", w.getTileEntityCount());
            m.put("time", w.getTime());
            m.put("weather", w.isThundering() ? "thunder" : w.hasStorm() ? "rain" : "clear");
            m.put("difficulty", w.getDifficulty().name().toLowerCase(Locale.ROOT));
            worlds.add(m);
        }
        s.put("worlds", worlds);
        List<Map<String, Object>> plugins = new ArrayList<>();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            plugins.add(Map.of("name", p.getName(), "version", p.getPluginMeta().getVersion(), "enabled", p.isEnabled()));
        }
        plugins.sort((a, b) -> String.valueOf(a.get("name")).compareToIgnoreCase(String.valueOf(b.get("name"))));
        s.put("plugins", plugins);
        return s;
    }

    /** Where a player is, how they are and what is around them. */
    private static Map<String, Object> surroundings(Player p, int radius) {
        Map<String, Object> m = new LinkedHashMap<>();
        Location l = p.getLocation();
        World w = l.getWorld();
        m.put("name", p.getName());
        m.put("uuid", p.getUniqueId().toString());
        m.put("world", w.getName());
        m.put("x", round(l.getX()));
        m.put("y", round(l.getY()));
        m.put("z", round(l.getZ()));
        m.put("yaw", round(l.getYaw()));
        m.put("pitch", round(l.getPitch()));
        m.put("chunk", List.of(l.getBlockX() >> 4, l.getBlockZ() >> 4));
        m.put("biome", l.getBlock().getBiome().getKey().toString());
        m.put("light", l.getBlock().getLightLevel());
        m.put("gameMode", p.getGameMode().name().toLowerCase(Locale.ROOT));
        m.put("health", round(p.getHealth()));
        var maxHealth = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        m.put("maxHealth", maxHealth == null ? 20 : round(maxHealth.getValue()));
        m.put("food", p.getFoodLevel());
        m.put("saturation", round(p.getSaturation()));
        m.put("level", p.getLevel());
        m.put("ping", p.getPing());
        m.put("client", p.getClientBrandName());
        m.put("protocol", p.getProtocolVersion());
        m.put("locale", p.locale().toString());
        m.put("viewDistance", p.getClientViewDistance());
        m.put("flying", p.isFlying());
        m.put("vehicle", p.getVehicle() == null ? null : p.getVehicle().getType().getKey().getKey());
        m.put("op", p.isOp());
        ItemStack hand = p.getInventory().getItemInMainHand();
        m.put("hand", hand.isEmpty() ? null : hand.getType().getKey().getKey() + (hand.getAmount() > 1 ? " x" + hand.getAmount() : ""));
        List<String> effects = new ArrayList<>();
        for (PotionEffect e : p.getActivePotionEffects()) {
            effects.add(e.getType().getKey().getKey() + " " + (e.getAmplifier() + 1)
                    + (e.isInfinite() ? "" : " (" + e.getDuration() / 20 + " s)"));
        }
        m.put("effects", effects);
        // Entities, block entities and players around them.
        Map<String, Integer> entities = new TreeMap<>();
        List<Map<String, Object>> players = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player other) {
                players.add(Map.of("name", other.getName(), "distance", round(other.getLocation().distance(l)),
                        "gameMode", other.getGameMode().name().toLowerCase(Locale.ROOT)));
            } else {
                entities.merge(e.getType().getKey().getKey(), 1, Integer::sum);
            }
        }
        m.put("entities", sorted(entities));
        m.put("players", players);
        Map<String, Integer> tiles = new TreeMap<>();
        int chunks = Math.max(1, (radius + 15) / 16);
        int cx = l.getBlockX() >> 4;
        int cz = l.getBlockZ() >> 4;
        int loaded = 0;
        for (int x = cx - chunks; x <= cx + chunks; x++) {
            for (int z = cz - chunks; z <= cz + chunks; z++) {
                if (!w.isChunkLoaded(x, z)) {
                    continue;
                }
                loaded++;
                Chunk c = w.getChunkAt(x, z);
                for (BlockState state : c.getTileEntities(false)) {
                    tiles.merge(state.getType().getKey().getKey(), 1, Integer::sum);
                }
            }
        }
        m.put("tiles", sorted(tiles));
        m.put("tileArea", (chunks * 2 + 1) + "x" + (chunks * 2 + 1) + " chunks, " + loaded + " loaded");
        return m;
    }

    private static List<Map<String, Object>> hotChunks() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (HotChunks.Chunk c : HotChunks.count(8)) {
            Map<String, Integer> types = new HashMap<>();
            c.types().forEach((type, n) -> types.put(type.getKey().getKey(), n));
            list.add(Map.of("world", c.world().getName(), "x", c.x() * 16 + 8, "z", c.z() * 16 + 8,
                    "count", c.count(), "types", sorted(types)));
        }
        return list;
    }

    /** The points as columns and rows - an object per second would be several times as big. */
    private static Map<String, Object> health(List<HealthMonitor.Point> points) {
        List<String> fields = List.of("time", "tps", "mspt", "msptP95", "msptMax", "cpu", "cpuSystem", "heapUsed",
                "heapMax", "gc", "gcMax", "players", "chunks", "entities", "tiles", "ping", "loaderChunks");
        List<List<Object>> rows = new ArrayList<>();
        for (HealthMonitor.Point p : points) {
            rows.add(List.of(p.time(), round(p.tps()), round(p.mspt()), round(p.msptP95()), round(p.msptMax()), round(p.cpu()),
                    round(p.cpuSystem()), p.heapUsed(), p.heapMax(), round(p.gc()), round(p.gcMax()), p.players(), p.chunks(),
                    p.entities(), p.tiles(), round(p.ping()), p.loaderChunks()));
        }
        return Map.of("fields", fields, "rows", rows);
    }

    private Map<String, Object> console(long since, boolean links, boolean ips) throws IOException {
        ZoneId zone = ZoneId.systemDefault();
        ServerLogFiles files = new ServerLogFiles(plugin.getServer().getWorldContainer().toPath().resolve("logs"), zone);
        ServerLogFiles.Result found = files.find(LocalDate.now(zone), since, 0, "", null, MAX_CONSOLE, false, links, ips);
        List<List<Object>> lines = new ArrayList<>();
        for (ServerLogFiles.Line line : found.lines()) {
            String text = line.text().length() > MAX_LINE ? line.text().substring(0, MAX_LINE) + " ..." : line.text();
            lines.add(List.of(line.time(), line.level(), text));
        }
        Collections.reverse(lines);
        return Map.of("lines", lines, "cut", found.more());
    }

    private List<Map<String, Object>> events(long since) {
        List<Map<String, Object>> list = new ArrayList<>();
        List<EventLog.Entry> found = new ArrayList<>(plugin.eventLog().find(null, "", since, 0, MAX_EVENTS));
        found.sort((a, b) -> Long.compare(a.time(), b.time()));
        for (EventLog.Entry e : found) {
            list.add(Map.of("time", e.time(), "source", e.source(), "event", e.event(), "text", e.text(),
                    "color", String.format("#%06x", e.color() & 0xFFFFFF)));
        }
        return list;
    }

    // ------------------------------------------------------------ sharing

    /**
     * The share token of a ticket's log (made on first use and kept): who knows it gets the sealed file, nothing else
     * - only MRtechnology can open that. Saved in the storage document ticket-log-shares.
     */
    public synchronized String share(int ticketId) {
        YamlConfiguration yaml = plugin.storage().loadYaml(SHARES, plugin.getLogger());
        for (String token : yaml.getKeys(false)) {
            if (yaml.getInt(token) == ticketId) {
                return token;
            }
        }
        byte[] random = new byte[24];
        RANDOM.nextBytes(random);
        String token = HexFormat.of().formatHex(random);
        yaml.set(token, ticketId);
        plugin.storage().saveYaml(SHARES, yaml);
        return token;
    }

    /** The ticket of a share token, or -1. */
    public synchronized int byToken(String token) {
        if (token == null || !token.matches("[0-9a-f]{48}")) {
            return -1;
        }
        return plugin.storage().loadYaml(SHARES, plugin.getLogger()).getInt(token, -1);
    }

    /** The sealed file of a ticket's log (for MRtechnology), or null if the ticket has no log. Blocking. */
    public Map<String, Object> sealed(int ticketId) throws java.security.GeneralSecurityException {
        byte[] data = load(ticketId);
        if (data == null) {
            return null;
        }
        try {
            // The part of a plugin (e.g. MRchunks' chunk loader) stays on this server - never sent to MRtechnology.
            String json;
            try (var in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(data))) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            com.google.gson.JsonObject object = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (object.has("module")) {
                object.remove("module");
                data = gzip(GSON.toJson(object));
            }
            return LogSeal.seal(data, Map.of("ticket", ticketId, "server", plugin.branding().name(),
                    "sealed", System.currentTimeMillis()));
        } catch (IOException e) {
            throw new java.security.GeneralSecurityException(e);
        }
    }

    // --------------------------------------------------------------- helpers

    private static List<List<Object>> sorted(Map<String, Integer> counts) {
        List<List<Object>> list = new ArrayList<>();
        counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> list.add(List.of(e.getKey(), e.getValue())));
        return list;
    }

    private static double round(double value) {
        return Double.isFinite(value) ? Math.round(value * 100) / 100.0 : 0;
    }

    static byte[] gzip(String json) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
}

package at.marcelr.mradmin.ticket;

import at.marcelr.mradmin.MRAdmin;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * The logging service of ticket logs (since 1.7): for every player the last 30 minutes of play time - every second the
 * position, health and food (track), every 5 seconds a frame (the entities in the 16 x 16 chunks around and the item in
 * hand), what they did with a time (blocks, items, damage, commands - same things within 5 seconds as one row), events
 * (deaths, teleports, joins) and what happened around them (despawned items, deaths, other players), so the map of a
 * ticket log plays smoothly and its chronicle has a time for everything. Only minutes the player was online count; a disconnect keeps everything, a restart empties
 * it (memory only).
 *
 * <p>Load and memory: every player is sampled once per 5 seconds, spread over the ticks; a chunk is read at most once
 * per 2.5 seconds and shared by everyone near it, and a chunk whose entities did not change keeps its old copy - an
 * unchanged farm costs nothing more over time. All lists are capped (also the number of recorded players).
 */
public final class PlayerRecorder implements Listener {

    /** Minutes of play time kept per player. */
    public static final int MINUTES = 30;
    /** Half the side of the area around a player (16 x 16 chunks). */
    static final int HALF = 128;
    private static final int SAMPLE_TICKS = 100;
    private static final int TRACK_TICKS = 20;
    /** Same action on the same thing within this time is one row of the chronicle. */
    private static final long MERGE_MS = 5_000;
    private static final int MAX_ACTIONS = 240;
    private static final long RESCAN_MS = 2_500;
    /** Entity groups (kind, 2 x 2 blocks) kept per chunk. */
    private static final int MAX_GROUPS_PER_CHUNK = 48;
    private static final int MAX_ROWS = 120;
    private static final int MAX_EVENTS = 60;
    private static final int MAX_COMMANDS = 30;
    /** Players kept at most - the ones offline the longest go first. */
    private static final int MAX_PLAYERS = 500;

    /** The entities of one chunk at one time: groups of {type, category, x, z, count}. Shared, never changed. */
    record ChunkEntities(int[] groups) {
    }

    /** One 5-second frame: where the player was and the entities around (non-empty chunks only). */
    record Frame(long time, String world, int x, int y, int z, ChunkEntities[] chunks, String hand) {
    }

    /** One minute of play of one player. */
    static final class Minute {
        final long start;
        final List<Frame> frames = new ArrayList<>(12);
        /** Every second: time, world, x, y, z, health, food. */
        final List<List<Object>> track = new ArrayList<>(60);
        /** What the player did, with a time: time, kind, what, amount (rows within 5 s merged). */
        final List<Object[]> actions = new ArrayList<>();
        final Map<String, Integer> broken = new TreeMap<>();
        final Map<String, Integer> placed = new TreeMap<>();
        final Map<String, Integer> picked = new TreeMap<>();
        final Map<String, Integer> dropped = new TreeMap<>();
        final Map<String, Double> damageTaken = new TreeMap<>();
        final Map<String, Double> damageDealt = new TreeMap<>();
        final List<Map<String, Object>> events = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        final List<List<Object>> despawnRows = new ArrayList<>();
        final Map<String, Integer> despawned = new TreeMap<>();
        final List<Map<String, Object>> deaths = new ArrayList<>();
        Set<String> nearby = new HashSet<>();
        final List<String> came = new ArrayList<>();
        final List<String> went = new ArrayList<>();

        Minute(long start) {
            this.start = start;
        }
    }

    private record Recording(Deque<Minute> minutes, long[] lastOnline) {
    }

    private record Scan(long time, ChunkEntities entities) {
    }

    private final MRAdmin plugin;
    private final Map<UUID, Recording> players = new ConcurrentHashMap<>();
    /** Last read of every chunk near a player: world|cx|cz -> time and entities (empty ones not kept). */
    private final Map<String, Scan> scans = new HashMap<>();
    /** Names of entity kinds and categories by number (the frames only keep numbers). */
    private final List<String> types = new ArrayList<>();
    private final Map<String, Integer> typeIds = new HashMap<>();
    static final List<String> CATEGORIES = List.of("hostile", "passive", "item", "projectile", "vehicle", "other");
    private final RegionTiles tiles;
    private BukkitTask task;
    private int tick;

    PlayerRecorder(MRAdmin plugin) {
        this.plugin = plugin;
        this.tiles = new RegionTiles(plugin);
    }

    public RegionTiles tiles() {
        return tiles;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sampleSlice, 1, 1);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** tickets.attach-log.hidden-commands: commands whose arguments are never recorded (passwords). */
    private Set<String> hiddenCommands() {
        return plugin.settings().tickets().attachLog().hiddenCommands();
    }

    // ------------------------------------------------------------- sampling

    /** The minute the player is in right now (a new one when the clock passed a minute). */
    private Minute now(Player p) {
        long minute = System.currentTimeMillis() / 60_000L * 60_000L;
        Recording rec = players.computeIfAbsent(p.getUniqueId(), k -> {
            trimPlayers();
            return new Recording(new ArrayDeque<>(), new long[1]);
        });
        rec.lastOnline()[0] = System.currentTimeMillis();
        Deque<Minute> minutes = rec.minutes();
        synchronized (minutes) {
            Minute last = minutes.peekLast();
            if (last == null || last.start != minute) {
                Minute next = new Minute(minute);
                if (last != null) {
                    next.nearby = last.nearby;
                }
                minutes.addLast(next);
                while (minutes.size() > MINUTES) {
                    minutes.pollFirst();
                }
                last = next;
            }
            return last;
        }
    }

    /** More players than kept: the one offline the longest is dropped. */
    private void trimPlayers() {
        while (players.size() >= MAX_PLAYERS) {
            UUID oldest = null;
            long time = Long.MAX_VALUE;
            for (Map.Entry<UUID, Recording> e : players.entrySet()) {
                if (Bukkit.getPlayer(e.getKey()) == null && e.getValue().lastOnline()[0] < time) {
                    time = e.getValue().lastOnline()[0];
                    oldest = e.getKey();
                }
            }
            if (oldest == null) {
                return;
            }
            players.remove(oldest);
        }
    }

    /** Every tick a slice of the players, so everyone is sampled once per 5 seconds without a spike. */
    private void sampleSlice() {
        tick = (tick + 1) % SAMPLE_TICKS;
        long time = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            int slot = Math.floorMod(p.getUniqueId().hashCode(), SAMPLE_TICKS);
            if (slot % TRACK_TICKS == tick % TRACK_TICKS) {
                // the track: once per second, cheap (no chunk reads)
                Location tl = p.getLocation();
                Minute tm = now(p);
                synchronized (tm) {
                    if (tm.track.size() < 90) {
                        tm.track.add(List.of(time, tl.getWorld().getName(), tl.getBlockX(), tl.getBlockY(), tl.getBlockZ(),
                                Math.round(p.getHealth() * 10) / 10.0, p.getFoodLevel()));
                    }
                }
            }
            if (slot != tick) {
                continue;
            }
            Minute m = now(p);
            Location l = p.getLocation();
            ChunkEntities[] chunks = scanAround(l, time);
            synchronized (m) {
                var held = p.getInventory().getItemInMainHand();
                m.frames.add(new Frame(time, l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), chunks,
                        held.getType().isAir() ? null : key(held.getType())));
                // other players coming into or leaving the area
                Set<String> near = new HashSet<>();
                for (Player o : l.getWorld().getPlayers()) {
                    Location ol = o.getLocation();
                    if (!o.equals(p) && Math.abs(ol.getBlockX() - l.getBlockX()) < HALF && Math.abs(ol.getBlockZ() - l.getBlockZ()) < HALF) {
                        near.add(o.getName());
                    }
                }
                for (String n : near) {
                    if (!m.nearby.contains(n) && m.came.size() < MAX_ROWS) {
                        m.came.add(n);
                        act(m, time, "came", n, 1);
                    }
                }
                for (String n : m.nearby) {
                    if (!near.contains(n) && m.went.size() < MAX_ROWS) {
                        m.went.add(n);
                        act(m, time, "went", n, 1);
                    }
                }
                m.nearby = near;
            }
            tiles.want(l);
        }
        if (tick == 0) {
            scans.values().removeIf(s -> time - s.time() > 60_000);
        }
    }

    /** The entities of the 16 x 16 chunks around a place - each chunk read at most every 2.5 seconds, shared. */
    private ChunkEntities[] scanAround(Location l, long time) {
        World w = l.getWorld();
        int pcx = l.getBlockX() >> 4;
        int pcz = l.getBlockZ() >> 4;
        List<ChunkEntities> out = new ArrayList<>();
        for (int cx = pcx - 8; cx < pcx + 8; cx++) {
            for (int cz = pcz - 8; cz < pcz + 8; cz++) {
                if (!w.isChunkLoaded(cx, cz)) {
                    continue;
                }
                String key = w.getName() + "|" + cx + "|" + cz;
                Scan scan = scans.get(key);
                if (scan == null || time - scan.time() > RESCAN_MS) {
                    ChunkEntities fresh = read(w.getChunkAt(cx, cz));
                    // unchanged: keep the old copy, so frames share it
                    ChunkEntities kept = scan != null && scan.entities() != null && fresh != null
                            && Arrays.equals(scan.entities().groups(), fresh.groups()) ? scan.entities() : fresh;
                    scan = new Scan(time, kept);
                    scans.put(key, scan);
                }
                if (scan.entities() != null) {
                    out.add(scan.entities());
                }
            }
        }
        return out.toArray(ChunkEntities[]::new);
    }

    /** One chunk: entities grouped by kind and 2 x 2 blocks, the biggest groups; null if there are none. */
    private ChunkEntities read(Chunk chunk) {
        Map<Long, int[]> groups = new HashMap<>();
        for (Entity e : chunk.getEntities()) {
            if (e instanceof Player) {
                continue;
            }
            String type = e instanceof Item item ? "item:" + item.getItemStack().getType().getKey().getKey()
                    : e.getType().getKey().getKey();
            int id = typeId(type);
            int cat = CATEGORIES.indexOf(LogArea.category(e));
            Location el = e.getLocation();
            int gx = el.getBlockX() >> 1 << 1;
            int gz = el.getBlockZ() >> 1 << 1;
            long key = ((long) id << 40) ^ ((long) (gx & 0xFFFFF) << 20) ^ (gz & 0xFFFFF);
            groups.computeIfAbsent(key, k -> new int[] {id, cat, gx, gz, 0})[4] += e instanceof Item item ? item.getItemStack().getAmount() : 1;
        }
        if (groups.isEmpty()) {
            return null;
        }
        List<int[]> list = new ArrayList<>(groups.values());
        // fixed order (biggest first, then by place) so an unchanged chunk gives the same array
        list.sort(Comparator.<int[]>comparingInt(g -> -g[4]).thenComparingInt(g -> g[0]).thenComparingInt(g -> g[2]).thenComparingInt(g -> g[3]));
        int n = Math.min(list.size(), MAX_GROUPS_PER_CHUNK);
        int[] flat = new int[n * 5];
        for (int i = 0; i < n; i++) {
            System.arraycopy(list.get(i), 0, flat, i * 5, 5);
        }
        return new ChunkEntities(flat);
    }

    private int typeId(String type) {
        synchronized (types) {
            Integer id = typeIds.get(type);
            if (id == null) {
                id = types.size();
                types.add(type);
                typeIds.put(type, id);
            }
            return id;
        }
    }

    // ------------------------------------------------------------- actions

    /** One row of the chronicle; the same kind and thing within 5 seconds adds up. Call inside synchronized (m). */
    private static void act(Minute m, long time, String kind, String what, double amount) {
        Object[] same = null;
        for (int i = m.actions.size() - 1; i >= 0 && i >= m.actions.size() - 6; i--) {
            Object[] a = m.actions.get(i);
            if ((long) a[0] < time - MERGE_MS) {
                break;
            }
            if (a[1].equals(kind) && a[2].equals(what)) {
                same = a;
                break;
            }
        }
        if (same != null) {
            same[3] = Math.round(((double) same[3] + amount) * 10) / 10.0;
        } else if (m.actions.size() < MAX_ACTIONS) {
            m.actions.add(new Object[] {time, kind, what, Math.round(amount * 10) / 10.0});
        }
    }

    private void event(Player p, String kind, String text) {
        Minute m = now(p);
        synchronized (m) {
            if (m.events.size() < MAX_EVENTS) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("time", System.currentTimeMillis());
                e.put("kind", kind);
                e.put("text", text);
                m.events.add(e);
            }
        }
    }

    private static String key(org.bukkit.Keyed keyed) {
        return keyed.getKey().getKey();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        event(event.getPlayer(), "join", event.getPlayer().getLocation().getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        event(event.getPlayer(), "quit", event.getReason().name().toLowerCase(Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Minute m = now(event.getPlayer());
        synchronized (m) {
            m.broken.merge(key(event.getBlock().getType()), 1, Integer::sum);
            act(m, System.currentTimeMillis(), "broken", key(event.getBlock().getType()), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Minute m = now(event.getPlayer());
        synchronized (m) {
            m.placed.merge(key(event.getBlockPlaced().getType()), 1, Integer::sum);
            act(m, System.currentTimeMillis(), "placed", key(event.getBlockPlaced().getType()), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p) {
            Minute m = now(p);
            synchronized (m) {
                m.picked.merge(key(event.getItem().getItemStack().getType()), event.getItem().getItemStack().getAmount(), Integer::sum);
                act(m, System.currentTimeMillis(), "picked", key(event.getItem().getItemStack().getType()), event.getItem().getItemStack().getAmount());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Minute m = now(event.getPlayer());
        synchronized (m) {
            m.dropped.merge(key(event.getItemDrop().getItemStack().getType()), event.getItemDrop().getItemStack().getAmount(), Integer::sum);
            act(m, System.currentTimeMillis(), "dropped", key(event.getItemDrop().getItemStack().getType()), event.getItemDrop().getItemStack().getAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && event.getFinalDamage() > 0) {
            String cause = event instanceof EntityDamageByEntityEvent by ? attacker(by.getDamager())
                    : event.getCause().name().toLowerCase(Locale.ROOT);
            Minute m = now(p);
            synchronized (m) {
                m.damageTaken.merge(cause, event.getFinalDamage(), Double::sum);
                act(m, System.currentTimeMillis(), "taken", cause, event.getFinalDamage());
            }
        }
        if (event instanceof EntityDamageByEntityEvent by && shooter(by.getDamager()) instanceof Player p
                && event.getFinalDamage() > 0) {
            String target = event.getEntity() instanceof Player o ? "player:" + o.getName() : key(event.getEntity().getType());
            Minute m = now(p);
            synchronized (m) {
                m.damageDealt.merge(target, event.getFinalDamage(), Double::sum);
                act(m, System.currentTimeMillis(), "dealt", target, event.getFinalDamage());
            }
        }
    }

    private static Entity shooter(Entity damager) {
        return damager instanceof Projectile pr && pr.getShooter() instanceof Entity e ? e : damager;
    }

    private static String attacker(Entity damager) {
        Entity e = shooter(damager);
        return e instanceof Player p ? "player:" + p.getName() : key(e.getType());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        String text = event.deathMessage() == null ? "" : PlainTextComponentSerializer.plainText().serialize(event.deathMessage());
        event(event.getEntity(), "death", text);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        boolean world = !from.getWorld().equals(to.getWorld());
        if (!world && from.distanceSquared(to) < 16 * 16) {
            return;
        }
        event(event.getPlayer(), "teleport", (world ? from.getWorld().getName() + " " : "") + from.getBlockX() + " " + from.getBlockY()
                + " " + from.getBlockZ() + " -> " + (world ? to.getWorld().getName() + " " : "") + to.getBlockX() + " "
                + to.getBlockY() + " " + to.getBlockZ() + " (" + event.getCause().name().toLowerCase(Locale.ROOT) + ")");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String line = event.getMessage().trim();
        if (line.length() < 2) {
            return;
        }
        String label = line.split(" ", 2)[0].substring(1).toLowerCase(Locale.ROOT);
        String name = label.contains(":") ? label.substring(label.indexOf(':') + 1) : label;
        // passwords never get into the log: those commands only by name
        String shown = hiddenCommands().contains(name) ? "/" + label + (line.contains(" ") ? " ***" : "")
                : line.length() > 80 ? line.substring(0, 80) + " ..." : line;
        Minute m = now(event.getPlayer());
        synchronized (m) {
            if (m.commands.size() < MAX_COMMANDS) {
                m.commands.add(shown);
                act(m, System.currentTimeMillis(), "command", shown, 1);
            }
        }
    }

    // --------------------------------------------------- around the players

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        Location l = event.getLocation();
        var stack = event.getEntity().getItemStack();
        for (Player p : l.getWorld().getPlayers()) {
            if (close(p, l)) {
                Minute m = now(p);
                synchronized (m) {
                    m.despawned.merge(key(stack.getType()), stack.getAmount(), Integer::sum);
                    if (m.despawnRows.size() < MAX_ROWS) {
                        m.despawnRows.add(List.of(System.currentTimeMillis(), l.getBlockX(), l.getBlockZ(), key(stack.getType()), stack.getAmount()));
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity e = event.getEntity();
        Location l = e.getLocation();
        String type = e instanceof Player pl ? "player:" + pl.getName() : key(e.getType());
        var last = e.getLastDamageCause();
        String cause = last == null ? null : last instanceof EntityDamageByEntityEvent by ? attacker(by.getDamager())
                : last.getCause().name().toLowerCase(Locale.ROOT);
        for (Player p : l.getWorld().getPlayers()) {
            if (close(p, l)) {
                Minute m = now(p);
                synchronized (m) {
                    if (m.deaths.size() < MAX_ROWS) {
                        Map<String, Object> d = new LinkedHashMap<>();
                        d.put("time", System.currentTimeMillis());
                        d.put("type", type);
                        d.put("x", l.getBlockX());
                        d.put("z", l.getBlockZ());
                        d.put("cause", cause);
                        m.deaths.add(d);
                    }
                }
            }
        }
    }

    private static boolean close(Player p, Location l) {
        Location pl = p.getLocation();
        return Math.abs(pl.getBlockX() - l.getBlockX()) < HALF && Math.abs(pl.getBlockZ() - l.getBlockZ()) < HALF;
    }

    // ---------------------------------------------------------------- output

    /**
     * The recorded play of a player (oldest minute first) with the terrain it needs, or null. Entities are written once
     * per distinct chunk state ({@code chunks}: id -> flat groups) and the frames name them by id - a log stays small
     * even with a long, busy recording. {@code every}: only every n-th frame (to keep a log under its size limit).
     */
    Map<String, Object> timeline(UUID player, int every) {
        Recording rec = players.get(player);
        if (rec == null) {
            return null;
        }
        List<Minute> copy;
        synchronized (rec.minutes()) {
            copy = new ArrayList<>(rec.minutes());
        }
        if (copy.isEmpty()) {
            return null;
        }
        Map<ChunkEntities, Integer> ids = new IdentityHashMap<>();
        List<int[]> chunkData = new ArrayList<>();
        List<Map<String, Object>> list = new ArrayList<>();
        Set<String> regions = new java.util.LinkedHashSet<>();
        int index = 0;
        for (Minute m : copy) {
            synchronized (m) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("start", m.start);
                List<List<Object>> frames = new ArrayList<>();
                for (Frame f : m.frames) {
                    if (index++ % every != 0 && f != m.frames.getLast()) {
                        continue;
                    }
                    List<Integer> refs = new ArrayList<>(f.chunks().length);
                    for (ChunkEntities c : f.chunks()) {
                        refs.add(ids.computeIfAbsent(c, k -> {
                            chunkData.add(k.groups());
                            return chunkData.size() - 1;
                        }));
                    }
                    List<Object> row = new ArrayList<>(List.of(f.time(), f.world(), f.x(), f.z(), refs));
                    row.add(f.hand());
                    frames.add(row);
                    regions.addAll(RegionTiles.around(f.world(), f.x(), f.z()));
                }
                o.put("frames", frames);
                o.put("track", every > 1 ? thin(m.track, every) : new ArrayList<>(m.track));
                List<List<Object>> actions = new ArrayList<>(m.actions.size());
                for (Object[] a : m.actions) {
                    actions.add(List.of(a));
                }
                o.put("actions", actions);
                o.put("broken", m.broken);
                o.put("placed", m.placed);
                o.put("picked", m.picked);
                o.put("dropped", m.dropped);
                o.put("damageTaken", rounded(m.damageTaken));
                o.put("damageDealt", rounded(m.damageDealt));
                o.put("events", m.events);
                o.put("commands", m.commands);
                o.put("despawned", m.despawned);
                o.put("despawnRows", m.despawnRows);
                o.put("deaths", m.deaths);
                o.put("came", m.came);
                o.put("went", m.went);
                list.add(o);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("minutes", list);
        synchronized (types) {
            result.put("types", List.copyOf(types));
        }
        result.put("categories", CATEGORIES);
        result.put("chunks", chunkData);
        result.put("tiles", tiles.get(regions));
        return result;
    }

    private static List<List<Object>> thin(List<List<Object>> rows, int every) {
        List<List<Object>> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (i % every == 0 || i == rows.size() - 1) {
                out.add(rows.get(i));
            }
        }
        return out;
    }

    private static Map<String, Double> rounded(Map<String, Double> values) {
        Map<String, Double> out = new TreeMap<>();
        values.forEach((k, v) -> out.put(k, Math.round(v * 10) / 10.0));
        return out;
    }
}

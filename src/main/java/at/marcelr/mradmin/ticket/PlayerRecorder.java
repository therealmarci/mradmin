package at.marcelr.mradmin.ticket;

import at.marcelr.mradmin.MRAdmin;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
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
 * The logging service of ticket logs (since 1.7): for every player the last 30 minutes of play time, minute by minute -
 * where they were, what they did (blocks, items, damage, deaths, teleports, commands), what happened around them
 * (entities, despawned items, deaths, other players) and the entities around them at the end of each minute. Only
 * minutes the player was online count; a disconnect keeps everything, only a restart empties it (memory only).
 * Every list is capped, so the minutes stay readable.
 */
public final class PlayerRecorder implements Listener {

    /** Minutes of play time kept per player. */
    public static final int MINUTES = 30;
    /** Half the side of the area around a player (16 x 16 chunks). */
    private static final int HALF = 128;
    private static final int SAMPLE_TICKS = 100;
    private static final int MAX_GROUPS = 300;
    private static final int MAX_ROWS = 40;
    private static final int MAX_COMMANDS = 20;
    /** Commands whose arguments are never recorded (passwords). */
    private static final Set<String> SECRET = Set.of("login", "l", "register", "reg", "changepassword", "changepw", "cp",
            "unregister", "2fa", "totp", "email");

    /** One minute of play of one player. */
    static final class Minute {
        final long start;
        String world;
        final List<int[]> path = new ArrayList<>();
        final List<Long> pathTime = new ArrayList<>();
        int cx;
        int cz;
        int y;
        List<List<Object>> entities = List.of();
        Map<String, Integer> counts = Map.of();
        final Map<String, Integer> broken = new TreeMap<>();
        final Map<String, Integer> placed = new TreeMap<>();
        final Map<String, Integer> picked = new TreeMap<>();
        final Map<String, Integer> dropped = new TreeMap<>();
        final Map<String, Double> damageTaken = new TreeMap<>();
        final Map<String, Double> damageDealt = new TreeMap<>();
        final List<Map<String, Object>> events = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        final Map<String, Integer> despawned = new TreeMap<>();
        final List<List<Object>> despawnRows = new ArrayList<>();
        final List<Map<String, Object>> deaths = new ArrayList<>();
        Set<String> nearby = new HashSet<>();
        final List<String> came = new ArrayList<>();
        final List<String> went = new ArrayList<>();

        Minute(long start) {
            this.start = start;
        }
    }

    private final MRAdmin plugin;
    private final Map<UUID, Deque<Minute>> players = new ConcurrentHashMap<>();
    private final RegionTiles tiles;
    private BukkitTask task;
    private int round;

    PlayerRecorder(MRAdmin plugin) {
        this.plugin = plugin;
        this.tiles = new RegionTiles(plugin);
    }

    public RegionTiles tiles() {
        return tiles;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, SAMPLE_TICKS, SAMPLE_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ------------------------------------------------------------- sampling

    /** The minute the player is in right now (a new one when the clock passed a minute); null if not recording. */
    private Minute now(Player p) {
        long minute = System.currentTimeMillis() / 60_000L * 60_000L;
        Deque<Minute> minutes = players.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        synchronized (minutes) {
            Minute last = minutes.peekLast();
            if (last == null || last.start != minute) {
                last = new Minute(minute);
                Location l = p.getLocation();
                last.world = l.getWorld().getName();
                last.cx = l.getBlockX();
                last.cz = l.getBlockZ();
                last.y = l.getBlockY();
                Minute before = minutes.peekLast();
                if (before != null) {
                    last.nearby = before.nearby;
                }
                minutes.addLast(last);
                while (minutes.size() > MINUTES) {
                    minutes.pollFirst();
                }
            }
            return last;
        }
    }

    /** Every 5 seconds: positions of all players; the entities around a twelfth of them (each player once a minute). */
    private void sample() {
        round = (round + 1) % 12;
        long time = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Minute m = now(p);
            Location l = p.getLocation();
            synchronized (m) {
                m.world = l.getWorld().getName();
                m.cx = l.getBlockX();
                m.cz = l.getBlockZ();
                m.y = l.getBlockY();
                int[] last = m.path.isEmpty() ? null : m.path.getLast();
                if (last == null || last[0] != l.getBlockX() || last[1] != l.getBlockZ()) {
                    m.path.add(new int[] {l.getBlockX(), l.getBlockZ()});
                    m.pathTime.add(time);
                }
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
                    }
                }
                for (String n : m.nearby) {
                    if (!near.contains(n) && m.went.size() < MAX_ROWS) {
                        m.went.add(n);
                    }
                }
                m.nearby = near;
            }
            tiles.want(l);
            if (Math.floorMod(p.getUniqueId().hashCode(), 12) == round) {
                scanEntities(p, m);
            }
        }
    }

    /** The entities in the 16 x 16 chunks around the player, grouped by kind and 2 x 2 blocks, plus counts per kind. */
    private static void scanEntities(Player p, Minute m) {
        Location l = p.getLocation();
        World w = l.getWorld();
        int pcx = l.getBlockX() >> 4;
        int pcz = l.getBlockZ() >> 4;
        Map<String, int[]> groups = new HashMap<>();
        Map<String, String> cats = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (int cx = pcx - 8; cx < pcx + 8; cx++) {
            for (int cz = pcz - 8; cz < pcz + 8; cz++) {
                if (!w.isChunkLoaded(cx, cz)) {
                    continue;
                }
                Chunk chunk = w.getChunkAt(cx, cz);
                for (Entity e : chunk.getEntities()) {
                    if (e instanceof Player) {
                        continue;
                    }
                    String type = e instanceof Item item ? "item:" + item.getItemStack().getType().getKey().getKey()
                            : e.getType().getKey().getKey();
                    int n = e instanceof Item item ? item.getItemStack().getAmount() : 1;
                    cats.putIfAbsent(type, LogArea.category(e));
                    counts.merge(type, n, Integer::sum);
                    Location el = e.getLocation();
                    int gx = el.getBlockX() >> 1 << 1;
                    int gz = el.getBlockZ() >> 1 << 1;
                    groups.computeIfAbsent(type + "|" + gx + "|" + gz, k -> new int[] {gx, gz, 0})[2] += n;
                }
            }
        }
        List<List<Object>> list = new ArrayList<>();
        groups.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String, int[]> e) -> e.getValue()[2]).reversed())
                .limit(MAX_GROUPS).forEach(e -> {
                    String type = e.getKey().substring(0, e.getKey().indexOf('|'));
                    list.add(List.of(type, cats.get(type), e.getValue()[0], e.getValue()[1], e.getValue()[2]));
                });
        synchronized (m) {
            m.entities = list;
            m.counts = counts;
        }
    }

    // ------------------------------------------------------------- actions

    private void event(Player p, String kind, String text) {
        Minute m = now(p);
        synchronized (m) {
            if (m.events.size() < MAX_ROWS) {
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
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Minute m = now(event.getPlayer());
        synchronized (m) {
            m.placed.merge(key(event.getBlockPlaced().getType()), 1, Integer::sum);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p) {
            Minute m = now(p);
            synchronized (m) {
                m.picked.merge(key(event.getItem().getItemStack().getType()), event.getItem().getItemStack().getAmount(), Integer::sum);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Minute m = now(event.getPlayer());
        synchronized (m) {
            m.dropped.merge(key(event.getItemDrop().getItemStack().getType()), event.getItemDrop().getItemStack().getAmount(), Integer::sum);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && event.getFinalDamage() > 0) {
            String cause = event.getCause().name().toLowerCase(Locale.ROOT);
            if (event instanceof EntityDamageByEntityEvent by) {
                cause = attacker(by.getDamager());
            }
            Minute m = now(p);
            synchronized (m) {
                m.damageTaken.merge(cause, event.getFinalDamage(), Double::sum);
            }
        }
        if (event instanceof EntityDamageByEntityEvent by && shooter(by.getDamager()) instanceof Player p
                && event.getFinalDamage() > 0) {
            String target = event.getEntity() instanceof Player o ? "player:" + o.getName() : key(event.getEntity().getType());
            Minute m = now(p);
            synchronized (m) {
                m.damageDealt.merge(target, event.getFinalDamage(), Double::sum);
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
        String label = line.split(" ", 2)[0].substring(1).toLowerCase(Locale.ROOT);
        String name = label.contains(":") ? label.substring(label.indexOf(':') + 1) : label;
        String shown = SECRET.contains(name) ? "/" + label + " ***" : line.length() > 80 ? line.substring(0, 80) + " ..." : line;
        Minute m = now(event.getPlayer());
        synchronized (m) {
            if (m.commands.size() < MAX_COMMANDS) {
                m.commands.add(shown);
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
     * The recorded minutes of a player (oldest first) with the terrain tiles they need, or null if nothing was
     * recorded. Any thread.
     */
    Map<String, Object> timeline(UUID player) {
        Deque<Minute> minutes = players.get(player);
        if (minutes == null) {
            return null;
        }
        List<Minute> copy;
        synchronized (minutes) {
            copy = new ArrayList<>(minutes);
        }
        if (copy.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        Set<String> regions = new java.util.LinkedHashSet<>();
        for (Minute m : copy) {
            synchronized (m) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("start", m.start);
                o.put("world", m.world);
                o.put("center", List.of(m.cx, m.cz));
                List<List<Object>> path = new ArrayList<>();
                for (int i = 0; i < m.path.size(); i++) {
                    path.add(List.of(m.pathTime.get(i), m.path.get(i)[0], m.path.get(i)[1]));
                }
                o.put("path", path);
                o.put("entities", m.entities);
                o.put("counts", m.counts);
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
                regions.addAll(RegionTiles.around(m.world, m.cx, m.cz));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("minutes", list);
        result.put("tiles", tiles.get(regions));
        return result;
    }

    private static Map<String, Double> rounded(Map<String, Double> values) {
        Map<String, Double> out = new TreeMap<>();
        values.forEach((k, v) -> out.put(k, Math.round(v * 10) / 10.0));
        return out;
    }
}

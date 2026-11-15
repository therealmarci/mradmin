package at.marcelr.mradmin.ticket;

import at.marcelr.mradmin.MRAdmin;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

/**
 * The area part of ticket logs (since 1.7): a map of the 16 x 16 chunks around the player with every entity, the
 * items that despawned there, deaths nearby, the player's way and inventory. To have the past at hand it keeps
 * small rolling records of despawns, deaths and player positions (only what the longest log span needs).
 * Every list is capped and grouped, so a log stays readable even next to a big farm.
 */
public final class LogArea implements Listener {

    /** 16 x 16 chunks around the player. */
    public static final int CHUNKS = 16;
    private static final long KEEP_MS = 120 * 60_000L;
    private static final int MAX_DESPAWNS = 50_000;
    private static final int MAX_DEATHS = 20_000;
    private static final int PATH_TICKS = 100;
    private static final int MAX_ENTITY_GROUPS = 2_500;
    private static final int MAX_DESPAWN_ROWS = 1_500;
    private static final int MAX_DEATH_ROWS = 300;
    private static final int SNAPSHOTS_PER_TICK = 32;

    private record Despawn(long time, UUID world, int x, int z, String item, int amount) {
    }

    private record Death(long time, UUID world, int x, int z, String type, String cause, String killer, String message) {
    }

    private record Spot(long time, UUID world, int x, int z) {
    }

    private final MRAdmin plugin;
    private final Deque<Despawn> despawns = new ArrayDeque<>();
    private final Deque<Death> deaths = new ArrayDeque<>();
    private final Map<UUID, Deque<Spot>> paths = new ConcurrentHashMap<>();
    private BukkitTask task;

    LogArea(MRAdmin plugin) {
        this.plugin = plugin;
    }

    /** Starts recording player positions (every 5 seconds). Server thread. */
    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::recordPaths, PATH_TICKS, PATH_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ------------------------------------------------------------- recording

    private void recordPaths() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            Deque<Spot> path = paths.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
            synchronized (path) {
                Spot last = path.peekLast();
                // standing still: only every minute a point, so an AFK player does not fill the record
                if (last == null || last.x() != l.getBlockX() || last.z() != l.getBlockZ() || now - last.time() > 60_000) {
                    path.addLast(new Spot(now, l.getWorld().getUID(), l.getBlockX(), l.getBlockZ()));
                }
                while (!path.isEmpty() && path.peekFirst().time() < now - KEEP_MS) {
                    path.pollFirst();
                }
            }
        }
        paths.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast().time() < now - KEEP_MS;
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        ItemStack stack = event.getEntity().getItemStack();
        Location l = event.getLocation();
        synchronized (despawns) {
            despawns.addLast(new Despawn(System.currentTimeMillis(), l.getWorld().getUID(), l.getBlockX(), l.getBlockZ(),
                    stack.getType().getKey().getKey(), stack.getAmount()));
            trim(despawns, MAX_DESPAWNS, Despawn::time);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity e = event.getEntity();
        Location l = e.getLocation();
        var last = e.getLastDamageCause();
        String cause = last == null ? null : last.getCause().name().toLowerCase(Locale.ROOT);
        String killer = e.getKiller() == null ? null : e.getKiller().getName();
        String message = null;
        if (event instanceof PlayerDeathEvent pd && pd.deathMessage() != null) {
            message = PlainTextComponentSerializer.plainText().serialize(pd.deathMessage());
        }
        String type = e instanceof Player p ? "player:" + p.getName() : e.getType().getKey().getKey();
        synchronized (deaths) {
            deaths.addLast(new Death(System.currentTimeMillis(), l.getWorld().getUID(), l.getBlockX(), l.getBlockZ(), type, cause,
                    killer, message));
            trim(deaths, MAX_DEATHS, Death::time);
        }
    }

    private static <T> void trim(Deque<T> deque, int max, java.util.function.ToLongFunction<T> time) {
        long old = System.currentTimeMillis() - KEEP_MS;
        while (deque.size() > max || (!deque.isEmpty() && time.applyAsLong(deque.peekFirst()) < old)) {
            deque.pollFirst();
        }
    }

    // --------------------------------------------------------------- capture

    /**
     * Everything of the area around a player. Server thread; the chunks are copied over a few ticks and the map is
     * drawn in the background - the result arrives a moment later.
     */
    CompletableFuture<Map<String, Object>> capture(Location l, Player player, long since) {
        World world = l.getWorld();
        int cx0 = (l.getBlockX() >> 4) - CHUNKS / 2;
        int cz0 = (l.getBlockZ() >> 4) - CHUNKS / 2;
        int x0 = cx0 << 4;
        int z0 = cz0 << 4;
        int size = CHUNKS * 16;
        Map<String, Object> area = new LinkedHashMap<>();
        area.put("world", world.getName());
        area.put("environment", world.getEnvironment().name().toLowerCase(Locale.ROOT));
        area.put("x0", x0);
        area.put("z0", z0);
        area.put("size", size);
        area.put("center", List.of(l.getBlockX(), l.getBlockZ()));
        if (player != null) {
            area.put("player", player.getName());
            area.put("inventory", inventory(player.getInventory()));
        }

        // Entities right now, grouped by type and 2 x 2 blocks; the list keeps the biggest groups.
        Map<String, int[]> groups = new HashMap<>();
        Map<String, String> categories = new HashMap<>();
        List<Chunk> loaded = new ArrayList<>();
        for (int cx = cx0; cx < cx0 + CHUNKS; cx++) {
            for (int cz = cz0; cz < cz0 + CHUNKS; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                Chunk chunk = world.getChunkAt(cx, cz);
                loaded.add(chunk);
                for (Entity e : chunk.getEntities()) {
                    if (e instanceof Player) {
                        continue;
                    }
                    String type = e instanceof Item item ? "item:" + item.getItemStack().getType().getKey().getKey()
                            : e.getType().getKey().getKey();
                    categories.putIfAbsent(type, category(e));
                    Location el = e.getLocation();
                    int gx = el.getBlockX() >> 1 << 1;
                    int gz = el.getBlockZ() >> 1 << 1;
                    int[] g = groups.computeIfAbsent(type + "|" + gx + "|" + gz, k -> new int[] {gx, gz, 0});
                    g[2] += e instanceof Item item ? item.getItemStack().getAmount() : 1;
                }
            }
        }
        List<List<Object>> entities = new ArrayList<>();
        groups.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String, int[]> e) -> e.getValue()[2]).reversed())
                .limit(MAX_ENTITY_GROUPS).forEach(e -> {
                    String type = e.getKey().substring(0, e.getKey().indexOf('|'));
                    entities.add(List.of(type, categories.get(type), e.getValue()[0], e.getValue()[1], e.getValue()[2]));
                });
        area.put("entities", entities);
        area.put("entitiesCut", groups.size() > MAX_ENTITY_GROUPS);
        area.put("players", nearbyPlayers(world, player, x0, z0, size));

        UUID wid = world.getUID();
        area.put("despawned", despawned(wid, since, x0, z0, size));
        area.put("deaths", deaths(wid, since, x0, z0, size));
        if (player != null) {
            area.put("path", path(player.getUniqueId(), wid, since));
        }

        // The chunks are copied a few per tick, then drawn off the server thread.
        CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
        List<ChunkSnapshot> snapshots = new ArrayList<>();
        boolean nether = world.getEnvironment() == World.Environment.NETHER;
        int fromY = nether ? Math.min(world.getMaxHeight() - 1, l.getBlockY() + 20) : world.getMaxHeight() - 1;
        int minY = world.getMinHeight();
        new org.bukkit.scheduler.BukkitRunnable() {
            int next;

            @Override
            public void run() {
                for (int i = 0; i < SNAPSHOTS_PER_TICK && next < loaded.size(); i++, next++) {
                    Chunk chunk = loaded.get(next);
                    if (chunk.isLoaded()) {
                        snapshots.add(chunk.getChunkSnapshot(!nether, false, false));
                    }
                }
                if (next >= loaded.size()) {
                    cancel();
                    Thread.ofVirtual().name("MRadmin-TicketMap").start(() -> {
                        try {
                            area.put("image", draw(snapshots, x0, z0, size, fromY, minY, !nether));
                        } catch (IOException | RuntimeException e) {
                            plugin.getLogger().fine("Ticket map not drawn: " + e);
                        }
                        result.complete(area);
                    });
                }
            }
        }.runTaskTimer(plugin, 1, 1);
        return result;
    }

    /** hostile, passive, item, projectile, vehicle or other - the colour of the point on the map. */
    private static String category(Entity e) {
        if (e instanceof Item) {
            return "item";
        }
        if (e instanceof Enemy) {
            return "hostile";
        }
        if (e instanceof Animals || e instanceof WaterMob || e instanceof Ambient) {
            return "passive";
        }
        if (e instanceof Projectile) {
            return "projectile";
        }
        if (e instanceof Vehicle) {
            return "vehicle";
        }
        return e instanceof LivingEntity ? "passive" : "other";
    }

    private static List<List<Object>> inventory(PlayerInventory inv) {
        List<List<Object>> list = new ArrayList<>();
        ItemStack[] contents = inv.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            List<String> extra = new ArrayList<>();
            var meta = stack.getItemMeta();
            if (meta != null && meta.hasCustomName()) {
                extra.add("\"" + PlainTextComponentSerializer.plainText().serialize(meta.customName()) + "\"");
            }
            for (Map.Entry<Enchantment, Integer> en : stack.getEnchantments().entrySet()) {
                extra.add(en.getKey().getKey().getKey() + " " + en.getValue());
            }
            if (meta instanceof org.bukkit.inventory.meta.Damageable d && d.hasDamage() && stack.getType().getMaxDurability() > 0) {
                extra.add((stack.getType().getMaxDurability() - d.getDamage()) + "/" + stack.getType().getMaxDurability());
            }
            list.add(List.of(slot, stack.getType().getKey().getKey(), stack.getAmount(), String.join(", ", extra)));
        }
        return list;
    }

    private static List<List<Object>> nearbyPlayers(World world, Player self, int x0, int z0, int size) {
        List<List<Object>> list = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            Location l = p.getLocation();
            if (!p.equals(self) && inside(l.getBlockX(), l.getBlockZ(), x0, z0, size)) {
                list.add(List.of(p.getName(), l.getBlockX(), l.getBlockZ()));
            }
        }
        return list;
    }

    private Map<String, Object> despawned(UUID world, long since, int x0, int z0, int size) {
        List<Despawn> found = new ArrayList<>();
        synchronized (despawns) {
            for (Despawn d : despawns) {
                if (d.time() >= since && d.world().equals(world) && inside(d.x(), d.z(), x0, z0, size)) {
                    found.add(d);
                }
            }
        }
        Map<String, Integer> summary = new HashMap<>();
        found.forEach(d -> summary.merge(d.item(), d.amount(), Integer::sum));
        List<List<Object>> rows = new ArrayList<>();
        for (Despawn d : found.subList(Math.max(0, found.size() - MAX_DESPAWN_ROWS), found.size())) {
            rows.add(List.of(d.time(), d.x(), d.z(), d.item(), d.amount()));
        }
        return Map.of("total", found.size(), "summary", top(summary), "rows", rows);
    }

    private Map<String, Object> deaths(UUID world, long since, int x0, int z0, int size) {
        List<Death> found = new ArrayList<>();
        synchronized (deaths) {
            for (Death d : deaths) {
                if (d.time() >= since && d.world().equals(world) && inside(d.x(), d.z(), x0, z0, size)) {
                    found.add(d);
                }
            }
        }
        Map<String, Integer> summary = new HashMap<>();
        found.forEach(d -> summary.merge(d.type().startsWith("player:") ? "player" : d.type(), 1, Integer::sum));
        List<Map<String, Object>> rows = new ArrayList<>();
        // players always, the rest newest first up to the limit
        List<Death> shown = new ArrayList<>(found.stream().filter(d -> d.type().startsWith("player:")).toList());
        List<Death> others = found.stream().filter(d -> !d.type().startsWith("player:")).toList();
        shown.addAll(others.subList(Math.max(0, others.size() - (MAX_DEATH_ROWS - Math.min(shown.size(), MAX_DEATH_ROWS))), others.size()));
        shown.sort(Comparator.comparingLong(Death::time));
        for (Death d : shown) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", d.time());
            m.put("x", d.x());
            m.put("z", d.z());
            m.put("type", d.type());
            m.put("cause", d.cause());
            m.put("killer", d.killer());
            m.put("message", d.message());
            rows.add(m);
        }
        return Map.of("total", found.size(), "summary", top(summary), "rows", rows);
    }

    private List<List<Object>> path(UUID player, UUID world, long since) {
        Deque<Spot> path = paths.get(player);
        List<List<Object>> list = new ArrayList<>();
        if (path == null) {
            return list;
        }
        synchronized (path) {
            for (Spot s : path) {
                if (s.time() >= since) {
                    // another world: a break in the line
                    list.add(s.world().equals(world) ? List.of(s.time(), s.x(), s.z()) : List.of(s.time()));
                }
            }
        }
        return list;
    }

    private static boolean inside(int x, int z, int x0, int z0, int size) {
        return x >= x0 && z >= z0 && x < x0 + size && z < z0 + size;
    }

    private static List<List<Object>> top(Map<String, Integer> counts) {
        List<List<Object>> list = new ArrayList<>();
        counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(30)
                .forEach(e -> list.add(List.of(e.getKey(), e.getValue())));
        return list;
    }

    // ------------------------------------------------------------------- map

    /**
     * The area from above, one pixel per block, shaded like a Minecraft map. In the Nether from a little above the
     * player downwards (the roof would hide everything). Unloaded chunks stay transparent.
     */
    private static String draw(List<ChunkSnapshot> snapshots, int x0, int z0, int size, int fromY, int minY, boolean surface)
            throws IOException {
        int[] heights = new int[size * size];
        int[] colors = new int[size * size];
        java.util.Arrays.fill(heights, Integer.MIN_VALUE);
        for (ChunkSnapshot chunk : snapshots) {
            int bx = (chunk.getX() << 4) - x0;
            int bz = (chunk.getZ() << 4) - z0;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int top = surface ? Math.min(fromY, chunk.getHighestBlockYAt(x, z) + 1) : fromY;
                    for (int y = top; y >= minY; y--) {
                        Material type = chunk.getBlockType(x, y, z);
                        if (type.isAir()) {
                            continue;
                        }
                        Color color = chunk.getBlockData(x, y, z).getMapColor();
                        if (color == null || color.asRGB() == 0) {
                            continue;
                        }
                        int i = (bz + z) * size + bx + x;
                        heights[i] = y;
                        colors[i] = color.asRGB();
                        break;
                    }
                }
            }
        }
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int i = z * size + x;
                if (heights[i] == Integer.MIN_VALUE) {
                    continue;
                }
                int north = z > 0 ? heights[i - size] : heights[i];
                double shade = north == Integer.MIN_VALUE || heights[i] == north ? 0.86 : heights[i] > north ? 1.0 : 0.71;
                int rgb = colors[i];
                int r = (int) (((rgb >> 16) & 0xFF) * shade);
                int g = (int) (((rgb >> 8) & 0xFF) * shade);
                int b = (int) ((rgb & 0xFF) * shade);
                image.setRGB(x, z, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }
}

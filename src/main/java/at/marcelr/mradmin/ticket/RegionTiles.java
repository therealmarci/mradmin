package at.marcelr.mradmin.ticket;

import at.marcelr.mradmin.MRAdmin;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Terrain pictures for the playable map of ticket logs (since 1.7): the world in squares of 16 x 16 chunks, each drawn
 * from above (one pixel per block, like {@link LogArea}) while a player is near and then shared by all players.
 * Only loaded chunks are drawn (nothing is loaded for it), one square at a time, kept in memory until a restart.
 */
public final class RegionTiles {

    /** Blocks per side of a square. */
    public static final int SIZE = 256;
    private static final long STALE_MS = 10 * 60_000L;
    /** A square with holes (chunks were not loaded yet, e.g. right after joining) is drawn again this soon. */
    private static final long INCOMPLETE_MS = 30_000L;
    private static final int MAX_TILES = 300;
    private static final int SNAPSHOTS_PER_TICK = 32;

    private record Tile(long time, String image, boolean complete) {
    }

    private record Job(String key, String world, int rx, int rz, int fromY) {
    }

    private final MRAdmin plugin;
    /** Least recently used first. */
    private final Map<String, Tile> tiles = new LinkedHashMap<>(64, 0.75f, true);
    private final Deque<Job> queue = new ArrayDeque<>();
    private final Set<String> queued = new java.util.HashSet<>();
    private boolean drawing;

    RegionTiles(MRAdmin plugin) {
        this.plugin = plugin;
    }

    static String key(String world, int rx, int rz) {
        return world + "|" + rx + "|" + rz;
    }

    /** The squares a view of 16 x 16 chunks around (x, z) touches - up to four. */
    static List<String> around(String world, int x, int z) {
        List<String> keys = new ArrayList<>();
        int rx0 = Math.floorDiv(x - SIZE / 2, SIZE);
        int rz0 = Math.floorDiv(z - SIZE / 2, SIZE);
        int rx1 = Math.floorDiv(x + SIZE / 2 - 1, SIZE);
        int rz1 = Math.floorDiv(z + SIZE / 2 - 1, SIZE);
        for (int rx = rx0; rx <= rx1; rx++) {
            for (int rz = rz0; rz <= rz1; rz++) {
                keys.add(key(world, rx, rz));
            }
        }
        return keys;
    }

    /** A player stands here: the squares around get drawn if missing or older than 10 minutes. Server thread. */
    void want(Location l) {
        World w = l.getWorld();
        boolean nether = w.getEnvironment() == World.Environment.NETHER;
        int fromY = nether ? Math.min(w.getMaxHeight() - 1, l.getBlockY() + 20) : w.getMaxHeight() - 1;
        long now = System.currentTimeMillis();
        for (String key : around(w.getName(), l.getBlockX(), l.getBlockZ())) {
            Tile tile;
            synchronized (tiles) {
                tile = tiles.get(key);
            }
            boolean stale = tile == null || now - tile.time() > (tile.complete() ? STALE_MS : INCOMPLETE_MS);
            if (stale && queued.add(key)) {
                String[] parts = key.split("\\|");
                queue.addLast(new Job(key, w.getName(), Integer.parseInt(parts[parts.length - 2]),
                        Integer.parseInt(parts[parts.length - 1]), fromY));
            }
        }
        next();
    }

    /** Draws the next square: chunks copied a few per tick, the picture made off the server thread. */
    private void next() {
        if (drawing || queue.isEmpty()) {
            return;
        }
        Job job = queue.pollFirst();
        World world = Bukkit.getWorld(job.world());
        if (world == null) {
            queued.remove(job.key());
            next();
            return;
        }
        drawing = true;
        int cx0 = job.rx() * (SIZE / 16);
        int cz0 = job.rz() * (SIZE / 16);
        boolean surface = world.getEnvironment() != World.Environment.NETHER;
        List<ChunkSnapshot> snapshots = new ArrayList<>();
        new BukkitRunnable() {
            int i;

            @Override
            public void run() {
                for (int n = 0; n < SNAPSHOTS_PER_TICK && i < 256; n++, i++) {
                    int cx = cx0 + i % 16;
                    int cz = cz0 + i / 16;
                    if (world.isChunkLoaded(cx, cz)) {
                        Chunk chunk = world.getChunkAt(cx, cz);
                        snapshots.add(chunk.getChunkSnapshot(surface, false, false));
                    }
                }
                if (i >= 256) {
                    cancel();
                    int minY = world.getMinHeight();
                    Thread.ofVirtual().name("MRadmin-RegionTile").start(() -> {
                        try {
                            if (!snapshots.isEmpty()) {
                                java.awt.image.BufferedImage image = LogArea.render(snapshots, cx0 << 4, cz0 << 4, SIZE, job.fromY(), minY, surface);
                                // what the last drawing had stays where chunks are not loaded now - holes only fill up
                                Tile old;
                                synchronized (tiles) {
                                    old = tiles.get(job.key());
                                }
                                java.awt.image.BufferedImage before = old == null ? null : LogArea.image(old.image());
                                if (before != null && before.getWidth() == SIZE && before.getHeight() == SIZE) {
                                    for (int y = 0; y < SIZE; y++) {
                                        for (int x = 0; x < SIZE; x++) {
                                            if ((image.getRGB(x, y) >>> 24) == 0) {
                                                image.setRGB(x, y, before.getRGB(x, y));
                                            }
                                        }
                                    }
                                }
                                boolean complete = snapshots.size() == 256 || (old != null && old.complete());
                                String png = LogArea.png(image);
                                synchronized (tiles) {
                                    tiles.put(job.key(), new Tile(System.currentTimeMillis(), png, complete));
                                    while (tiles.size() > MAX_TILES) {
                                        tiles.remove(tiles.keySet().iterator().next());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            plugin.getLogger().fine("Terrain square " + job.key() + " not drawn: " + e);
                        }
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            queued.remove(job.key());
                            drawing = false;
                            next();
                        });
                    });
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    /** The pictures of these squares that exist: key -> {world, x0, z0, image}. Any thread. */
    Map<String, Object> get(Collection<String> keys) {
        Map<String, Object> out = new LinkedHashMap<>();
        synchronized (tiles) {
            for (String key : keys) {
                Tile tile = tiles.get(key);
                if (tile != null) {
                    String[] parts = key.split("\\|");
                    int rx = Integer.parseInt(parts[parts.length - 2]);
                    int rz = Integer.parseInt(parts[parts.length - 1]);
                    out.put(key, Map.of("world", key.substring(0, key.length() - parts[parts.length - 2].length()
                            - parts[parts.length - 1].length() - 2), "x0", rx * SIZE, "z0", rz * SIZE, "image", tile.image()));
                }
            }
        }
        return out;
    }
}

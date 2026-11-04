package at.marcelr.mradmin.map;

import at.marcelr.mradmin.MRAdmin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MRadmin's own map of the Nether ceiling: squaremap can render a world only once and shows the Nether from the inside,
 * so the bedrock ceiling and what players built on it are drawn here - one pixel per block, seen from above, in the
 * same picture layout as squaremap (512 blocks per picture at the closest zoom, each zoom further out half the size),
 * into {@code plugins/MRadmin/map-roof/<folder>-roof/<zoom>/<x>_<z>.png}.
 * <p>
 * Once completely (all generated chunks, read from the region files, at {@code chunks-per-second}), afterwards only
 * chunks where something changes up there (blocks placed, broken, blown up; chunks around players on the ceiling).
 * Chunks are copied on the server thread ({@link ChunkSnapshot}), everything else runs on one worker thread.
 */
final class RoofRenderer implements Listener {

    /** Ending of the folder names: the roof of minecraft_the_nether is minecraft_the_nether-roof. */
    static final String SUFFIX = "-roof";
    private static final int TILE = 512;
    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    /** Below the ceiling there is nothing to see from above: a few blocks under from-y the bedrock is solid. */
    private static final int DEPTH = 6;

    /** A Nether world drawn here: its squaremap folder and closest zoom. */
    private record Target(World world, String folder, int zoomMax) {
    }

    private record Job(UUID world, int x, int z) {
    }

    private final MRAdmin plugin;
    private final MapSettings.Roof settings;
    private final Path dir;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MRadmin-nether-roof");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    // Server thread.
    private final Map<UUID, Target> targets = new HashMap<>();
    private final Map<UUID, Set<Long>> dirty = new HashMap<>();
    private final Deque<Job> full = new ArrayDeque<>();
    /** Chunks of the complete render still to do, per world - at 0 the world is marked complete. */
    private final Map<UUID, Integer> remaining = new HashMap<>();
    private int loading;
    private int ticks;
    private @Nullable BukkitTask task;

    // Worker thread: the closest-zoom pictures being drawn, and which of them changed since the last save.
    private final Map<String, BufferedImage> open = new HashMap<>();
    private final Set<String> changed = new LinkedHashSet<>();
    /** Closest zoom per folder - read by the worker, also while stopping. */
    private final Map<String, Integer> zooms = new java.util.concurrent.ConcurrentHashMap<>();

    RoofRenderer(MRAdmin plugin, MapSettings.Roof settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.dir = plugin.getDataFolder().toPath().resolve("map-roof");
    }

    /** Folder of all roof pictures. */
    Path dir() {
        return dir;
    }

    /** Whether the ceiling of squaremap's folder is drawn here. */
    boolean coversFolder(String folder) {
        return zooms.containsKey(folder);
    }

    /** Height from which a player stands on the ceiling. */
    int fromY() {
        return settings.fromY();
    }

    /** Whether the world's ceiling is drawn here. */
    boolean covers(World world) {
        return targets.containsKey(world.getUID());
    }

    /** @param worlds squaremap's worlds ({@link MapService#worlds()}): only Nether worlds squaremap renders get a roof */
    void start(List<Map<String, Object>> worlds) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NETHER) {
                continue;
            }
            String folder = world.getKey().asString().replace(':', '_');
            worlds.stream().filter(w -> folder.equals(w.get("folder"))).findFirst().ifPresent(w ->
                    targets.put(world.getUID(), new Target(world, folder, ((Number) w.get("zoomMax")).intValue())));
            Target added = targets.get(world.getUID());
            if (added != null) {
                zooms.put(folder, added.zoomMax());
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        if (settings.fullRender()) {
            for (Target target : targets.values()) {
                if (!Files.exists(dir.resolve(target.folder() + SUFFIX).resolve("complete"))) {
                    queueAll(target);
                }
            }
        }
        plugin.getLogger().info("Nether ceiling map: " + targets.size() + " world(s), from y " + settings.fromY() + ".");
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(this);
        targets.clear();
        full.clear();
        dirty.clear();
        worker.execute(this::save);
        worker.shutdown();
        try {
            worker.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------ what to draw

    /** All generated chunks of the world, from the headers of its region files (read in the background). */
    private void queueAll(Target target) {
        Path regions = target.world().getWorldFolder().toPath().resolve("region");
        UUID id = target.world().getUID();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Job> jobs = new ArrayList<>();
            try (DirectoryStream<Path> files = Files.newDirectoryStream(regions, "r.*.mca")) {
                for (Path file : files) {
                    Matcher m = REGION.matcher(file.getFileName().toString());
                    if (m.matches()) {
                        chunksIn(file, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), id, jobs);
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.FINE, "No region files for the Nether ceiling in " + regions, e);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!targets.containsKey(id)) {
                    return;
                }
                full.addAll(jobs);
                remaining.merge(id, jobs.size(), Integer::sum);
                plugin.getLogger().info("Nether ceiling map: rendering " + jobs.size() + " chunks of "
                        + target.world().getName() + " in the background.");
                if (jobs.isEmpty()) {
                    finished(id);
                }
            });
        });
    }

    /** A region file starts with 1024 locations, one per chunk; 0 = the chunk was never generated. */
    private static void chunksIn(Path file, int rx, int rz, UUID world, List<Job> jobs) throws IOException {
        byte[] header = new byte[4096];
        try (InputStream in = Files.newInputStream(file)) {
            if (in.readNBytes(header, 0, header.length) < header.length) {
                return;
            }
        }
        for (int i = 0; i < 1024; i++) {
            int at = i * 4;
            if ((header[at] | header[at + 1] | header[at + 2] | header[at + 3]) != 0) {
                jobs.add(new Job(world, rx * 32 + (i & 31), rz * 32 + (i >> 5)));
            }
        }
    }

    private void mark(Block block) {
        if (block.getY() >= settings.fromY() - DEPTH && targets.containsKey(block.getWorld().getUID())) {
            dirty.computeIfAbsent(block.getWorld().getUID(), k -> new HashSet<>())
                    .add(Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        mark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        mark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        mark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::mark);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::mark);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(ChunkLoadEvent event) {
        if (event.isNewChunk() && targets.containsKey(event.getWorld().getUID())) {
            dirty.computeIfAbsent(event.getWorld().getUID(), k -> new HashSet<>()).add(event.getChunk().getChunkKey());
        }
    }

    // ------------------------------------------------------------ server thread

    /** Every second: changed chunks first, then the complete render, both within chunks-per-second. */
    private void tick() {
        ticks++;
        // Whatever the events missed (water, pistons, commands): the chunks around players up there.
        if (ticks % 30 == 0) {
            for (Target target : targets.values()) {
                for (Player p : target.world().getPlayers()) {
                    if (p.getLocation().getY() >= settings.fromY()) {
                        Set<Long> set = dirty.computeIfAbsent(target.world().getUID(), k -> new HashSet<>());
                        int cx = p.getLocation().getBlockX() >> 4;
                        int cz = p.getLocation().getBlockZ() >> 4;
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                set.add(Chunk.getChunkKey(cx + dx, cz + dz));
                            }
                        }
                    }
                }
            }
        }
        int budget = settings.chunksPerSecond();
        for (var entry : dirty.entrySet()) {
            Target target = targets.get(entry.getKey());
            var it = entry.getValue().iterator();
            while (target != null && it.hasNext() && budget > 0) {
                long key = it.next();
                it.remove();
                budget--;
                read(target, (int) key, (int) (key >> 32), false);
            }
        }
        while (budget > 0 && loading < settings.chunksPerSecond() && !full.isEmpty()) {
            Job job = full.poll();
            Target target = targets.get(job.world());
            if (target != null) {
                budget--;
                read(target, job.x(), job.z(), true);
            }
        }
        if (ticks % 10 == 0) {
            worker.execute(this::save);
        }
    }

    /** Copies the chunk (loading it in the background if needed, never generating one) and hands it to the worker. */
    private void read(Target target, int x, int z, boolean complete) {
        World world = target.world();
        if (world.isChunkLoaded(x, z)) {
            ChunkSnapshot snapshot = world.getChunkAt(x, z).getChunkSnapshot(false, false, false);
            worker.execute(() -> draw(target, snapshot));
            done(target, complete);
            return;
        }
        loading++;
        world.getChunkAtAsync(x, z, false).whenComplete((chunk, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            loading--;
            if (chunk != null && targets.containsKey(world.getUID())) {
                ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
                worker.execute(() -> draw(target, snapshot));
            }
            done(target, complete);
        }));
    }

    private void done(Target target, boolean complete) {
        if (!complete) {
            return;
        }
        UUID id = target.world().getUID();
        int left = remaining.merge(id, -1, Integer::sum);
        if (left <= 0) {
            finished(id);
        }
    }

    /** The complete render of a world is through: save and remember it, so the next start does not do it again. */
    private void finished(UUID id) {
        remaining.remove(id);
        Target target = targets.get(id);
        if (target == null) {
            return;
        }
        worker.execute(() -> {
            save();
            try {
                Path folder = dir.resolve(target.folder() + SUFFIX);
                Files.createDirectories(folder);
                Files.writeString(folder.resolve("complete"), String.valueOf(System.currentTimeMillis()));
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not mark the Nether ceiling map as complete", e);
            }
            plugin.getLogger().info("Nether ceiling map of " + target.world().getName() + " rendered completely.");
        });
    }

    // ------------------------------------------------------------ worker thread

    /** One chunk: the highest block from the top down to the ceiling, shaded like a Minecraft map (higher = lighter). */
    private void draw(Target target, ChunkSnapshot chunk) {
        int top = target.world().getMaxHeight() - 1;
        int bottom = Math.max(target.world().getMinHeight(), settings.fromY() - DEPTH);
        int[] heights = new int[256];
        int[] colors = new int[256];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int i = z * 16 + x;
                heights[i] = Integer.MIN_VALUE;
                for (int y = top; y >= bottom; y--) {
                    Material type = chunk.getBlockType(x, y, z);
                    if (type.isAir()) {
                        continue;
                    }
                    Color color = chunk.getBlockData(x, y, z).getMapColor();
                    if (color == null || color.asRGB() == 0) {
                        continue;
                    }
                    heights[i] = y;
                    colors[i] = color.asRGB();
                    break;
                }
            }
        }
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        String key = target.folder() + "/" + Math.floorDiv(baseX, TILE) + "_" + Math.floorDiv(baseZ, TILE);
        BufferedImage image = open.computeIfAbsent(key, k -> load(target, Math.floorDiv(baseX, TILE),
                Math.floorDiv(baseZ, TILE)));
        int px = Math.floorMod(baseX, TILE);
        int pz = Math.floorMod(baseZ, TILE);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int i = z * 16 + x;
                int argb = 0;
                if (heights[i] != Integer.MIN_VALUE) {
                    // Compared with the block to the north, like Minecraft's maps (the first row stays flat).
                    int north = z > 0 ? heights[i - 16] : heights[i];
                    double shade = north == Integer.MIN_VALUE || heights[i] == north ? 0.86
                            : heights[i] > north ? 1.0 : 0.71;
                    argb = 0xFF000000 | shadeColor(colors[i], shade);
                }
                image.setRGB(px + x, pz + z, argb);
            }
        }
        changed.add(key);
    }

    private static int shadeColor(int rgb, double factor) {
        int r = (int) (((rgb >> 16) & 0xFF) * factor);
        int g = (int) (((rgb >> 8) & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private BufferedImage load(Target target, int tx, int tz) {
        Path file = picture(target.folder(), target.zoomMax(), tx, tz);
        if (Files.isRegularFile(file)) {
            try {
                BufferedImage read = ImageIO.read(file.toFile());
                if (read != null && read.getWidth() == TILE && read.getHeight() == TILE) {
                    BufferedImage copy = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
                    copy.getGraphics().drawImage(read, 0, 0, null);
                    return copy;
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.FINE, "Unreadable roof picture " + file + ", drawing it anew", e);
            }
        }
        return new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
    }

    private Path picture(String folder, int zoom, int tx, int tz) {
        return dir.resolve(folder + SUFFIX).resolve(String.valueOf(zoom)).resolve(tx + "_" + tz + ".png");
    }

    /** Writes the changed pictures and builds the zooms further out from them (four pictures into one). */
    private void save() {
        if (changed.isEmpty()) {
            open.clear();
            return;
        }
        Set<String> parents = new LinkedHashSet<>();
        for (String key : changed) {
            BufferedImage image = open.get(key);
            String folder = key.substring(0, key.indexOf('/'));
            String[] xz = key.substring(key.indexOf('/') + 1).split("_");
            int tx = Integer.parseInt(xz[0]);
            int tz = Integer.parseInt(xz[1]);
            Integer zoomMax = zooms.get(folder);
            if (image == null || zoomMax == null) {
                continue;
            }
            write(picture(folder, zoomMax, tx, tz), image);
            if (zoomMax > 0) {
                parents.add(folder + "/" + (zoomMax - 1) + "/" + Math.floorDiv(tx, 2) + "_" + Math.floorDiv(tz, 2));
            }
        }
        changed.clear();
        open.clear();
        while (!parents.isEmpty()) {
            Set<String> next = new LinkedHashSet<>();
            for (String key : parents) {
                String[] parts = key.split("/");
                String folder = parts[0];
                int zoom = Integer.parseInt(parts[1]);
                String[] xz = parts[2].split("_");
                int tx = Integer.parseInt(xz[0]);
                int tz = Integer.parseInt(xz[1]);
                write(picture(folder, zoom, tx, tz), combine(folder, zoom + 1, tx, tz));
                if (zoom > 0) {
                    next.add(folder + "/" + (zoom - 1) + "/" + Math.floorDiv(tx, 2) + "_" + Math.floorDiv(tz, 2));
                }
            }
            parents = next;
        }
    }

    /** One picture of a zoom from the four of the next closer zoom, each at half size. */
    private BufferedImage combine(String folder, int childZoom, int tx, int tz) {
        BufferedImage out = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                Path file = picture(folder, childZoom, tx * 2 + dx, tz * 2 + dz);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                try {
                    BufferedImage child = ImageIO.read(file.toFile());
                    if (child != null) {
                        g.drawImage(child, dx * TILE / 2, dz * TILE / 2, TILE / 2, TILE / 2, null);
                    }
                } catch (IOException e) {
                    plugin.getLogger().log(Level.FINE, "Unreadable roof picture " + file, e);
                }
            }
        }
        g.dispose();
        return out;
    }

    private void write(Path file, BufferedImage image) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            ImageIO.write(image, "png", temp.toFile());
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save the Nether ceiling picture " + file, e);
        }
    }
}

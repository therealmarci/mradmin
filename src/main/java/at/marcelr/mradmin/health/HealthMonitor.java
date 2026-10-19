package at.marcelr.mradmin.health;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.Settings;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

import static at.marcelr.mradmin.Messages.tag;

/**
 * Health of the server over time, like spark's health report: every 10 seconds one point with TPS, MSPT (average,
 * 95th percentile, maximum of the last 100 ticks), CPU, memory, garbage collection, players, chunks and entities.
 * <p>
 * Only values the JVM and Paper keep anyway are read - a sample costs well under a millisecond. The last 24 hours stay
 * in memory at full resolution; for longer ranges every 5 minutes are combined into one point, which is also stored
 * (health.history: 24h, 7d or 30d) and so survives restarts. Points are read by web threads (synchronized).
 * <p>
 * If the TPS stays below health.alert.below-tps for health.alert.minutes, the team (mradmin.status.health) and the
 * Discord log are told once, and again when it has recovered.
 */
public final class HealthMonitor {

    /** One measurement. CPU in percent (-1 = unknown), memory in MB, gc = milliseconds spent in GC per 10 seconds. */
    public record Point(long time, double tps, double mspt, double msptP95, double msptMax, double cpu, double cpuSystem,
                        long heapUsed, long heapMax, double gc, int players, int chunks, int entities, int tiles) {
    }

    /** One world right now. */
    public record WorldInfo(String name, String environment, int chunks, int entities, int tiles, int players) {
    }

    private static final long STEP = 10_000L;
    private static final long FINE_KEEP = 24 * 3_600_000L;
    private static final long COARSE_STEP = 5 * 60_000L;
    private static final Gson GSON = new Gson();

    private final MRAdmin plugin;
    private final Deque<Point> fine = new ArrayDeque<>();
    private final Deque<Point> coarse = new ArrayDeque<>();
    private final List<Point> bucket = new ArrayList<>();
    private volatile List<WorldInfo> worlds = List.of();
    private BukkitTask task;
    private long lastGcTime = -1;
    private long lastPrune;
    private long lowSince;
    private boolean alerted;

    public HealthMonitor(MRAdmin plugin) {
        this.plugin = plugin;
    }

    private Settings.HealthSettings settings() {
        return plugin.settings().health();
    }

    // --------------------------------------------------------------- lifecycle

    /** Loads the stored history and samples every 10 seconds from now on. */
    public void start() {
        stop();
        long since = System.currentTimeMillis() - settings().history();
        synchronized (this) {
            coarse.clear();
            for (String json : plugin.storage().loadHealth(since)) {
                try {
                    coarse.add(GSON.fromJson(json, Point.class));
                } catch (RuntimeException ignored) {
                    // a broken line
                }
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, 100L, STEP / 50);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ----------------------------------------------------------------- sample

    /** Server thread: one point (and the current worlds). */
    private void sample() {
        long now = System.currentTimeMillis();
        long[] ticks = Bukkit.getTickTimes().clone();
        Arrays.sort(ticks);
        double p95 = ticks.length == 0 ? 0 : ticks[(int) Math.min(ticks.length - 1, Math.ceil(ticks.length * 0.95) - 1)] / 1e6;
        double max = ticks.length == 0 ? 0 : ticks[ticks.length - 1] / 1e6;

        double cpu = -1;
        double cpuSystem = -1;
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            cpu = percent(os.getProcessCpuLoad());
            cpuSystem = percent(os.getCpuLoad());
        }
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcTime += Math.max(0, gc.getCollectionTime());
        }
        double gc = lastGcTime < 0 ? 0 : Math.max(0, gcTime - lastGcTime);
        lastGcTime = gcTime;

        int chunks = 0;
        int entities = 0;
        int tiles = 0;
        List<WorldInfo> list = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            int c = world.getChunkCount();
            int e = world.getEntityCount();
            int t = world.getTileEntityCount();
            chunks += c;
            entities += e;
            tiles += t;
            list.add(new WorldInfo(world.getName(), world.getEnvironment().name().toLowerCase(Locale.ROOT), c, e, t,
                    world.getPlayerCount()));
        }
        worlds = List.copyOf(list);

        double tps = Math.min(20, Bukkit.getTPS()[0]);
        Point point = new Point(now, round(tps), round(Bukkit.getAverageTickTime()), round(p95), round(max), cpu, cpuSystem,
                heap.getUsed() / 1_048_576, (heap.getMax() > 0 ? heap.getMax() : heap.getCommitted()) / 1_048_576, gc,
                Bukkit.getOnlinePlayers().size(), chunks, entities, tiles);
        add(point);
        alert(tps, now);
    }

    private synchronized void add(Point point) {
        fine.addLast(point);
        while (!fine.isEmpty() && fine.peekFirst().time() < point.time() - FINE_KEEP) {
            fine.removeFirst();
        }
        // Every 5 minutes: one combined point for the long ranges, stored so it survives restarts.
        if (!bucket.isEmpty() && bucket.getFirst().time() / COARSE_STEP != point.time() / COARSE_STEP) {
            Point combined = combine(bucket);
            coarse.addLast(combined);
            plugin.storage().addHealth(combined.time(), GSON.toJson(combined));
            bucket.clear();
        }
        bucket.add(point);
        long limit = point.time() - settings().history();
        while (!coarse.isEmpty() && coarse.peekFirst().time() < limit) {
            coarse.removeFirst();
        }
        if (point.time() - lastPrune > 3_600_000L) {
            lastPrune = point.time();
            plugin.storage().pruneHealth(limit);
        }
    }

    /** Averages; for the spikes (95 %, maximum) the highest value, so a lag spike never disappears in an average. */
    private static Point combine(List<Point> points) {
        Point last = points.getLast();
        return new Point(last.time(), avg(points, Point::tps), avg(points, Point::mspt),
                maxOf(points, Point::msptP95), maxOf(points, Point::msptMax), avg(points, Point::cpu),
                avg(points, Point::cpuSystem), (long) avg(points, Point::heapUsed), last.heapMax(), avg(points, Point::gc),
                (int) Math.round(maxOf(points, Point::players)), (int) Math.round(avg(points, Point::chunks)),
                (int) Math.round(avg(points, Point::entities)), (int) Math.round(avg(points, Point::tiles)));
    }

    // ----------------------------------------------------------------- reading

    /**
     * The points of the last {@code millis}, at most {@code max} of them (neighbours combined like the 5-minute
     * points). Up to 24 hours at full resolution, longer from the 5-minute points.
     */
    public synchronized List<Point> range(long millis, int max) {
        long since = System.currentTimeMillis() - millis;
        Deque<Point> source = millis <= FINE_KEEP ? fine : coarse;
        List<Point> points = new ArrayList<>();
        for (Point p : source) {
            if (p.time() >= since) {
                points.add(p);
            }
        }
        if (source == coarse) {
            // The newest minutes are not combined yet.
            points.addAll(bucket);
        }
        if (points.size() <= max) {
            return points;
        }
        List<Point> result = new ArrayList<>(max);
        double per = points.size() / (double) max;
        for (int i = 0; i < max; i++) {
            int from = (int) Math.floor(i * per);
            int to = (int) Math.min(points.size(), Math.floor((i + 1) * per));
            if (to > from) {
                result.add(combine(points.subList(from, to)));
            }
        }
        return result;
    }

    public synchronized Point latest() {
        return fine.peekLast();
    }

    public List<WorldInfo> worlds() {
        return worlds;
    }

    // ------------------------------------------------------------------- alert

    private void alert(double tps, long now) {
        Settings.HealthSettings s = settings();
        if (!s.alert()) {
            return;
        }
        if (tps < s.alertTps()) {
            if (lowSince == 0) {
                lowSince = now;
            }
            if (!alerted && now - lowSince >= s.alertMinutes() * 60_000L) {
                alerted = true;
                String value = String.format(Locale.ROOT, "%.1f", tps);
                plugin.getLogger().warning("TPS has been below " + s.alertTps() + " for " + s.alertMinutes() + " minutes (now " + value + ").");
                plugin.messages().broadcastTo(Perms.STATUS_HEALTH, "health-alert", tag("tps", value),
                        tag("minutes", s.alertMinutes()));
                plugin.discord().log("health-alert", "tps", value, "minutes", s.alertMinutes(),
                        "mspt", String.format(Locale.ROOT, "%.1f", Bukkit.getAverageTickTime()));
            }
        } else if (tps >= s.alertTps() + 1) {
            if (alerted) {
                String value = String.format(Locale.ROOT, "%.1f", tps);
                plugin.getLogger().info("TPS has recovered (" + value + ").");
                plugin.messages().broadcastTo(Perms.STATUS_HEALTH, "health-recovered", tag("tps", value));
                plugin.discord().log("health-recovered", "tps", value);
            }
            alerted = false;
            lowSince = 0;
        }
    }

    // ----------------------------------------------------------------- helpers

    private static double percent(double load) {
        return load < 0 ? -1 : round(load * 100);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static double avg(List<Point> points, ToDoubleFunction<Point> f) {
        return round(points.stream().mapToDouble(f).average().orElse(0));
    }

    private static double maxOf(List<Point> points, ToDoubleFunction<Point> f) {
        return points.stream().mapToDouble(f).max().orElse(0);
    }
}

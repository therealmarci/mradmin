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
 * Health of the server over time, like spark's health report: every second one point with TPS (ticks of that
 * second), MSPT (average, 95th percentile, maximum of the last 100 ticks), CPU, memory, garbage collection, players,
 * chunks and entities.
 * <p>
 * Only values the JVM and Paper keep anyway are read - a sample costs well under a millisecond; counting the ticks is
 * one tiny task per tick. Three resolutions keep memory small: the last hour per second, the last 24 hours as
 * 10-second points, longer as 5-minute points - those are also stored (health.history: 24h, 7d or 30d) and so survive
 * restarts. Combined points keep the highest spike (95 %, maximum), so a lag spike never vanishes in an average.
 * Points are read by web threads (synchronized).
 * <p>
 * If the TPS (1-minute average, so a single spike does not count) stays below health.alert.below-tps for
 * health.alert.minutes, the team (mradmin.status.health) and the Discord log are told once, and again when it has
 * recovered.
 */
public final class HealthMonitor {

    /**
     * One measurement. CPU in percent (-1 = unknown), memory in MB, gc = milliseconds spent in GC per second, ping = average
     * of the online players in ms (-1 = nobody online), loaderChunks = chunks MRchunks keeps loaded (-1 = no MRchunks).
     */
    public record Point(long time, double tps, double mspt, double msptP95, double msptMax, double cpu, double cpuSystem,
                        long heapUsed, long heapMax, double gc, int players, int chunks, int entities, int tiles,
                        double ping, int loaderChunks) {
    }

    /** One world right now. */
    public record WorldInfo(String name, String environment, int chunks, int entities, int tiles, int players) {
    }

    private static final long HOUR = 3_600_000L;
    private static final long DAY = 24 * HOUR;
    private static final long TEN_SECONDS = 10_000L;
    private static final long FIVE_MINUTES = 5 * 60_000L;
    private static final Gson GSON = new Gson();

    private final MRAdmin plugin;
    /** per second, last hour */
    private final Deque<Point> seconds = new ArrayDeque<>();
    /** 10-second points, last 24 hours */
    private final Deque<Point> tens = new ArrayDeque<>();
    /** 5-minute points, health.history (stored) */
    private final Deque<Point> coarse = new ArrayDeque<>();
    private final List<Point> tenBucket = new ArrayList<>();
    private final List<Point> coarseBucket = new ArrayList<>();
    private volatile List<WorldInfo> worlds = List.of();
    private BukkitTask sampler;
    private BukkitTask tickCounter;
    private int ticks;
    private long lastSample;
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

    /** Loads the stored history and samples every second from now on. */
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
        lastSample = System.currentTimeMillis();
        ticks = 0;
        tickCounter = Bukkit.getScheduler().runTaskTimer(plugin, () -> ticks++, 1L, 1L);
        sampler = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, 20L, 20L);
    }

    public void stop() {
        if (sampler != null) {
            sampler.cancel();
            sampler = null;
        }
        if (tickCounter != null) {
            tickCounter.cancel();
            tickCounter = null;
        }
    }

    // ----------------------------------------------------------------- sample

    /** Server thread, every 20 ticks: one point (and the current worlds). */
    private void sample() {
        long now = System.currentTimeMillis();
        double elapsed = Math.max(0.001, (now - lastSample) / 1000.0);
        // Ticks in the time since the last sample - the TPS of this second (Paper only offers 1-minute averages).
        double tps = Math.min(20, ticks / elapsed);
        ticks = 0;
        lastSample = now;

        long[] times = Bukkit.getTickTimes().clone();
        Arrays.sort(times);
        double p95 = times.length == 0 ? 0 : times[(int) Math.min(times.length - 1, Math.ceil(times.length * 0.95) - 1)] / 1e6;
        double max = times.length == 0 ? 0 : times[times.length - 1] / 1e6;

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
        double gc = lastGcTime < 0 ? 0 : Math.max(0, gcTime - lastGcTime) / elapsed;
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

        double ping = Bukkit.getOnlinePlayers().stream().mapToInt(org.bukkit.entity.Player::getPing).average().orElse(-1);

        add(new Point(now, round(tps), round(Bukkit.getAverageTickTime()), round(p95), round(max), cpu, cpuSystem,
                heap.getUsed() / 1_048_576, (heap.getMax() > 0 ? heap.getMax() : heap.getCommitted()) / 1_048_576,
                round(gc), Bukkit.getOnlinePlayers().size(), chunks, entities, tiles, ping < 0 ? -1 : round(ping),
                loaderChunks()));
        alert(Math.min(20, Bukkit.getTPS()[0]), now);
    }

    private synchronized void add(Point point) {
        long now = point.time();
        seconds.addLast(point);
        trim(seconds, now - HOUR);

        // Every 10 seconds one point for the day view.
        if (!tenBucket.isEmpty() && tenBucket.getFirst().time() / TEN_SECONDS != now / TEN_SECONDS) {
            Point ten = combine(tenBucket);
            tens.addLast(ten);
            trim(tens, now - DAY);
            tenBucket.clear();
            coarseBucket.add(ten);
        }
        tenBucket.add(point);

        // Every 5 minutes one point for the long ranges, stored so it survives restarts.
        if (!coarseBucket.isEmpty() && coarseBucket.getFirst().time() / FIVE_MINUTES != now / FIVE_MINUTES) {
            Point combined = combine(coarseBucket);
            coarse.addLast(combined);
            plugin.storage().addHealth(combined.time(), GSON.toJson(combined));
            coarseBucket.clear();
        }
        long limit = now - settings().history();
        trim(coarse, limit);
        if (now - lastPrune > HOUR) {
            lastPrune = now;
            plugin.storage().pruneHealth(limit);
        }
    }

    private static void trim(Deque<Point> points, long before) {
        while (!points.isEmpty() && points.peekFirst().time() < before) {
            points.removeFirst();
        }
    }

    /** Averages; for the spikes (95 %, maximum) the highest value, so a lag spike never disappears in an average. */
    private static Point combine(List<Point> points) {
        Point last = points.getLast();
        return new Point(last.time(), avg(points, Point::tps), avg(points, Point::mspt),
                maxOf(points, Point::msptP95), maxOf(points, Point::msptMax), avg(points, Point::cpu),
                avg(points, Point::cpuSystem), (long) avg(points, Point::heapUsed), last.heapMax(), avg(points, Point::gc),
                (int) Math.round(maxOf(points, Point::players)), (int) Math.round(avg(points, Point::chunks)),
                (int) Math.round(avg(points, Point::entities)), (int) Math.round(avg(points, Point::tiles)),
                known(points, Point::ping), (int) Math.round(known(points, Point::loaderChunks)));
    }

    // ----------------------------------------------------------------- reading

    /**
     * The points of the last {@code millis}, at most {@code max} of them (neighbours combined like above). Up to an
     * hour per second, up to a day from the 10-second points, longer from the 5-minute points.
     */
    public synchronized List<Point> range(long millis, int max) {
        long since = System.currentTimeMillis() - millis;
        List<Point> points = new ArrayList<>();
        if (millis <= HOUR) {
            collect(seconds, since, points);
        } else if (millis <= DAY) {
            collect(tens, since, points);
            points.addAll(tenBucket.isEmpty() ? List.of() : List.of(combine(tenBucket)));
        } else {
            collect(coarse, since, points);
            if (!coarseBucket.isEmpty()) {
                points.add(combine(coarseBucket));
            }
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

    private static void collect(Deque<Point> source, long since, List<Point> into) {
        for (Point p : source) {
            if (p.time() >= since) {
                into.add(p);
            }
        }
    }

    public synchronized Point latest() {
        return seconds.peekLast();
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
                plugin.push("lag", "lag", "/health", null, "tps", value, "minutes", s.alertMinutes());
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

    /** Average of the known values (-1 = unknown, e.g. nobody online), -1 if none is known. */
    private static double known(List<Point> points, ToDoubleFunction<Point> f) {
        return round(points.stream().mapToDouble(f).filter(v -> v >= 0).average().orElse(-1));
    }

    /** Chunks the loaders of MRchunks keep loaded - only if MRchunks runs and offers web data, else -1. */
    private int loaderChunks() {
        Settings.HubButton hook = at.marcelr.mradmin.web.ChunkRoutes.hook(plugin);
        return hook != null && plugin.hooks().web(hook, "chunks", java.util.Map.of()) instanceof Number n ? n.intValue() : -1;
    }

    private static double maxOf(List<Point> points, ToDoubleFunction<Point> f) {
        return points.stream().mapToDouble(f).max().orElse(0);
    }
}

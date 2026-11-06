package at.marcelr.mradmin.versions;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Texts;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * Restarts or stops the server after a countdown, announced like {@code /mradmin announce restart <time>} (presets
 * {@code restart} / {@code restart-now}, for a stop {@code stop} / {@code stop-now} - own texts there apply here
 * too). With the starter (mrserver) MRadmin tells it what to do and it brings the server back. Without it,
 * {@code versions.restart-method} decides: {@code stop} (a start script, watchdog or panel brings the server back),
 * {@code script} (restart-script from spigot.yml) or {@code none} (no restarts - a server cannot start itself
 * again). Main thread.
 */
public final class RestartManager {

    /** Seconds left at which the countdown is announced again. */
    private static final List<Long> STEPS = List.of(1800L, 900L, 600L, 300L, 120L, 60L, 30L, 10L, 5L, 4L, 3L, 2L, 1L);

    private final MRAdmin plugin;
    private BukkitTask task;
    private long endsAt;
    private String by;
    private boolean restart = true;

    public RestartManager(MRAdmin plugin) {
        this.plugin = plugin;
    }

    public boolean running() {
        return task != null;
    }

    /** When the server goes down (epoch ms), 0 if nothing is planned. */
    public long endsAt() {
        return task == null ? 0 : endsAt;
    }

    public String by() {
        return by;
    }

    /** Whether the planned countdown ends in a restart (otherwise a stop). */
    public boolean restarting() {
        return restart;
    }

    /** Restart with a countdown. */
    public boolean start(long delayMillis, String by) {
        return start(delayMillis, by, true);
    }

    /**
     * @param restart true = restart, false = stop (the server stays down; only with the starter or a script that
     *                knows the difference - without the starter a stop is like restart-method stop)
     * @return false if a countdown is already running or restarts are off ({@link #enabled()})
     */
    public boolean start(long delayMillis, String by, boolean restart) {
        if (task != null || (restart && !enabled())) {
            return false;
        }
        this.by = by;
        this.restart = restart;
        endsAt = System.currentTimeMillis() + delayMillis;
        // The first announcement is the start itself - the next one is the next step below it (not 4m 59s after 5m).
        long[] lastStep = {(delayMillis + 999) / 1000};
        String time = delayMillis <= 0 ? "-" : Texts.duration(delayMillis);
        plugin.getLogger().info(by + (restart ? " restarts" : " stops") + " the server in " + Texts.duration(delayMillis) + ".");
        plugin.discord().log(restart ? "server-restart" : "server-stop", "admin", by, "time", time);
        if (delayMillis > 0) {
            announce(delayMillis, true);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long left = endsAt - System.currentTimeMillis();
            long seconds = (left + 999) / 1000;
            if (left <= 0) {
                task.cancel();
                goDown();
                return;
            }
            for (long step : STEPS) {
                if (seconds <= step && lastStep[0] > step && seconds * 1000 < delayMillis - 500) {
                    announce(seconds * 1000, false);
                    break;
                }
            }
            lastStep[0] = seconds;
        }, 20L, 20L);
        return true;
    }

    /** Whether something brings the server back: the starter or {@code versions.restart-method}. */
    public boolean enabled() {
        return plugin.starter().active() || !plugin.settings().versions().restartMethod().equals("none");
    }

    /** @return false if nothing was planned */
    public boolean cancel(String by) {
        if (task == null) {
            return false;
        }
        task.cancel();
        task = null;
        plugin.getLogger().info(by + " cancelled the " + (restart ? "restart." : "stop."));
        plugin.messages().broadcast(restart ? "restart-cancelled" : "stop-cancelled");
        plugin.discord().log(restart ? "server-restart-cancelled" : "server-stop-cancelled", "admin", by);
        return true;
    }

    private void announce(long millis, boolean first) {
        String preset = restart ? "restart" : "stop";
        if (plugin.announcer().preset(preset).isPresent()) {
            // Only the first step may go to the community Discord (the preset's cooldown would stop the rest anyway).
            plugin.announcer().announcePreset(preset, List.of(Texts.duration(millis)), by, first);
        }
    }

    private void goDown() {
        String preset = restart ? "restart-now" : "stop-now";
        if (plugin.announcer().preset(preset).isPresent()) {
            plugin.announcer().announcePreset(preset, List.of(), by, false);
        }
        plugin.starter().prepare(restart);
        // A moment to read it, then down.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().info((restart ? "Restarting" : "Stopping") + " the server (requested by " + by + ").");
            if (restart && !plugin.starter().active() && plugin.settings().versions().restartMethod().equals("script")) {
                Bukkit.restart();
            } else {
                Bukkit.shutdown();
            }
        }, 60L);
    }
}

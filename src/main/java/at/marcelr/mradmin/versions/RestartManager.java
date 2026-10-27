package at.marcelr.mradmin.versions;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Texts;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * Restarts the server after a countdown, announced like {@code /mradmin announce restart <time>} (the presets
 * {@code restart} and {@code restart-now} - own texts there apply here too). At the end the server stops
 * ({@code versions.restart-method: stop} - a start script with a loop, a watchdog or a panel brings it back) or runs
 * the server's own restart ({@code script}: restart-script from spigot.yml). With {@code none} (default) there is no
 * restart, because a server cannot start itself again. Main thread.
 */
public final class RestartManager {

    /** Seconds left at which the countdown is announced again. */
    private static final List<Long> STEPS = List.of(1800L, 900L, 600L, 300L, 120L, 60L, 30L, 10L, 5L, 4L, 3L, 2L, 1L);

    private final MRAdmin plugin;
    private BukkitTask task;
    private long endsAt;
    private String by;

    public RestartManager(MRAdmin plugin) {
        this.plugin = plugin;
    }

    public boolean running() {
        return task != null;
    }

    /** When the server goes down (epoch ms), 0 if no restart is planned. */
    public long endsAt() {
        return task == null ? 0 : endsAt;
    }

    public String by() {
        return by;
    }

    /** @return false if a restart is already counting down or restarts are off ({@link #enabled()}) */
    public boolean start(long delayMillis, String by) {
        if (task != null || !enabled()) {
            return false;
        }
        this.by = by;
        endsAt = System.currentTimeMillis() + delayMillis;
        long[] lastStep = {Long.MAX_VALUE};
        plugin.getLogger().info(by + " restarts the server in " + Texts.duration(delayMillis) + ".");
        plugin.discord().log("server-restart", "admin", by, "time", delayMillis <= 0 ? "-" : Texts.duration(delayMillis));
        if (delayMillis > 0) {
            announce(delayMillis, true);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long left = endsAt - System.currentTimeMillis();
            long seconds = (left + 999) / 1000;
            if (left <= 0) {
                task.cancel();
                restartNow();
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

    /** Whether the owner set up something that brings the server back ({@code versions.restart-method}). */
    public boolean enabled() {
        return !plugin.settings().versions().restartMethod().equals("none");
    }

    /** @return false if no restart was planned */
    public boolean cancel(String by) {
        if (task == null) {
            return false;
        }
        task.cancel();
        task = null;
        plugin.getLogger().info(by + " cancelled the restart.");
        plugin.messages().broadcast("restart-cancelled");
        plugin.discord().log("server-restart-cancelled", "admin", by);
        return true;
    }

    private void announce(long millis, boolean first) {
        if (plugin.announcer().preset("restart").isPresent()) {
            // Only the first step may go to the community Discord (the preset's cooldown would stop the rest anyway).
            plugin.announcer().announcePreset("restart", List.of(Texts.duration(millis)), by, first);
        }
    }

    private void restartNow() {
        if (plugin.announcer().preset("restart-now").isPresent()) {
            plugin.announcer().announcePreset("restart-now", List.of(), by, false);
        }
        // A moment to read it, then down.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().info("Restarting the server (requested by " + by + ").");
            if (plugin.settings().versions().restartMethod().equals("script")) {
                Bukkit.restart();
            } else {
                Bukkit.shutdown();
            }
        }, 60L);
    }
}

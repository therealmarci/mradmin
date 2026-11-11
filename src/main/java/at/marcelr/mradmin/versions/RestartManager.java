package at.marcelr.mradmin.versions;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Texts;
import at.marcelr.mradmin.player.Npcs;
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

    /** A planned restart checks for updates once more this long before the end. */
    private static final long RECHECK_BEFORE = 5 * 60_000L;

    private final MRAdmin plugin;
    private BukkitTask task;
    private long endsAt;
    private String by;
    private boolean restart = true;
    private String reason;

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
        return start(delayMillis, by, true, null);
    }

    public boolean start(long delayMillis, String by, boolean restart) {
        return start(delayMillis, by, restart, null);
    }

    /**
     * @param restart true = restart, false = stop (the server stays down; only with the starter or a script that
     *                knows the difference - without the starter a stop is like restart-method stop)
     * @param reason  why (shown in console, log and team Discord); empty = MRadmin names one itself (waiting
     *                updates, otherwise "no reason given")
     * @return false if a countdown is already running or restarts are off ({@link #enabled()})
     */
    public boolean start(long delayMillis, String by, boolean restart, String reason) {
        if (task != null || (restart && !enabled())) {
            return false;
        }
        if (plugin.waitingRoom().roomRestarting()) {
            // The waiting room restarts right now - players would have nowhere to go (callers say so themselves).
            plugin.getLogger().info(by + " wanted to " + (restart ? "restart" : "stop")
                    + " the server - not now, the waiting room is restarting.");
            return false;
        }
        this.by = by;
        this.restart = restart;
        this.reason = clean(reason).isEmpty() ? autoReason() : clean(reason);
        endsAt = System.currentTimeMillis() + delayMillis;
        // The first announcement is the start itself - the next one is the next step below it (not 4m 59s after 5m).
        long[] lastStep = {(delayMillis + 999) / 1000};
        // Planned restarts look for updates once more 5 minutes before the end (or right away if less is left).
        boolean[] rechecked = {!(restart && "schedule".equals(by))};
        String time = delayMillis <= 0 ? "-" : Texts.duration(delayMillis);
        plugin.getLogger().info(by + (restart ? " restarts" : " stops") + " the server in " + Texts.duration(delayMillis)
                + " (reason: " + this.reason + ").");
        plugin.discord().log(restart ? "server-restart" : "server-stop", "admin", by, "time", time, "reason", this.reason);
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
            if (!rechecked[0] && left <= RECHECK_BEFORE) {
                rechecked[0] = true;
                plugin.starter().recheckBeforeRestart();
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
            // Community Discord only if switched on (server.discord-announce), and only the first step.
            boolean discord = first && plugin.starter().settings() != null && plugin.starter().settings().discordAnnounce();
            plugin.announcer().announcePreset(preset, List.of(Texts.duration(millis)), by, discord);
        }
    }

    /** Why the planned countdown runs, null if none. */
    public String reason() {
        return task == null ? null : reason;
    }

    /** One line, at most 200 characters. */
    static String clean(String reason) {
        if (reason == null) {
            return "";
        }
        String s = reason.replaceAll("[\\r\\n\\t]+", " ").strip();
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    /** Without an own reason: what waits for the restart (plugin updates, a new Paper build), else "no reason given". */
    private String autoReason() {
        java.util.List<String> waiting = new java.util.ArrayList<>();
        if (plugin.versions() != null) {
            for (VersionManager.Entry e : plugin.versions().list()) {
                String v = e.staged != null ? e.staged : e.pending;
                if (v != null) {
                    waiting.add(e.name + " " + v);
                }
            }
        }
        var paper = plugin.starter().paperUpdate();
        if (paper != null && plugin.starter().active() && "auto".equals(plugin.starter().settings().paperUpdate())) {
            waiting.add("Paper " + paper.id());
        }
        return plugin.messages().server(() -> waiting.isEmpty() ? plugin.messages().plain("restart-reason-none")
                : plugin.messages().plain("restart-reason-updates").replace("{list}", String.join(", ", waiting)));
    }

    private void goDown() {
        String preset = restart ? "restart-now" : "stop-now";
        if (plugin.announcer().preset(preset).isPresent()) {
            plugin.announcer().announcePreset(preset, List.of(), by, false);
        }
        plugin.starter().prepare(restart);
        // A moment to read it, then down. Behind a proxy with a waiting room the players move there first
        // (waiting-room.on-restart) and the server goes down once the proxy had time to move them. If the waiting room
        // is restarting itself right now, wait for it up to 2 minutes - otherwise the players would be kicked after all.
        waitForRoom(0);
    }

    private void waitForRoom(int waited) {
        var room = plugin.waitingRoom();
        if (room.active() && room.config().onRestart() && !room.roomUp() && waited < 120 && !Npcs.online().isEmpty()) {
            if (waited == 0) {
                plugin.getLogger().info("The waiting room does not answer - waiting for it up to 2 minutes before going down.");
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> waitForRoom(waited + 5), 100L);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int sent = plugin.waitingRoom().sendAllBeforeStop();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getLogger().info((restart ? "Restarting" : "Stopping") + " the server (requested by " + by + ", reason: " + reason + ").");
                if (restart && !plugin.starter().active() && plugin.settings().versions().restartMethod().equals("script")) {
                    Bukkit.restart();
                } else {
                    Bukkit.shutdown();
                }
            }, sent > 0 ? 40L : 1L);
        }, 60L);
    }
}

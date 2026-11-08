package at.marcelr.mradmin.versions;

import at.marcelr.mradmin.starter.JavaFlags;
import at.marcelr.mradmin.util.DurationUtil;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * config.yml section {@code server}: what the starter (mrserver) does - memory, Java options, Paper updates, planned
 * restarts, crash rules. Planned restarts also work without the starter if {@code versions.restart-method} brings the
 * server back.
 *
 * @param countdown  ms before planned restarts and for mrserver stop/restart without a time
 * @param plainStop  "stop" or "restart": what a plain /stop does with the starter
 * @param discordAnnounce countdowns of restarts and stops also go to the community Discord (default off)
 */
public record ServerSettings(String memoryMin, String memoryMax, String javaFlags, String paperUpdate,
                             List<LocalTime> restartTimes, long countdown, String plainStop, int crashMax,
                             int crashWindowMinutes, int hangSeconds, int stopTimeoutSeconds, boolean discordAnnounce) {

    /**
     * "05:00" or "5:00"; a number is a time written without quotes, which YAML reads as minutes (5:30 -> 330).
     *
     * @return null if it is no time of day
     */
    static LocalTime time(Object raw) {
        if (raw instanceof Number n) {
            int minutes = n.intValue();
            return n.doubleValue() == minutes && minutes >= 0 && minutes < 1440 ? LocalTime.of(minutes / 60, minutes % 60) : null;
        }
        var m = java.util.regex.Pattern.compile("^([01]?\\d|2[0-3]):([0-5]\\d)$").matcher(String.valueOf(raw).trim());
        return m.matches() ? LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))) : null;
    }

    public static ServerSettings load(FileConfiguration c, Logger log) {
        String min = JavaFlags.memory(c.getString("server.memory-min", "1G"));
        String max = JavaFlags.memory(c.getString("server.memory-max", "4G"));
        if (min == null || max == null) {
            log.warning("server.memory-min / memory-max must look like 1G or 4096M - using 1G / 4G.");
            min = min == null ? "1G" : min;
            max = max == null ? "4G" : max;
        }
        List<String> flags = JavaFlags.split(c.getString("server.java-flags", ""));
        for (String flag : flags) {
            String problem = JavaFlags.problem(flag);
            if (problem != null) {
                log.warning("server.java-flags: '" + flag + "' is ignored - " + problem + ".");
            }
        }
        String update = c.getString("server.paper-update", "auto").trim().toLowerCase(Locale.ROOT);
        if (!update.matches("off|notify|auto")) {
            log.warning("server.paper-update '" + update + "' is unknown (off, notify, auto), using auto.");
            update = "auto";
        }
        List<LocalTime> times = new ArrayList<>();
        for (Object raw : c.getList("server.restart-times", List.of())) {
            LocalTime time = time(raw);
            if (time == null) {
                log.warning("server.restart-times: '" + raw + "' is not a time like \"05:00\", skipped.");
            } else if (!times.contains(time)) {
                times.add(time);
            }
        }
        long countdown = DurationUtil.parse(c.getString("server.countdown", "15m"));
        if (countdown < 0 || countdown > 3_600_000L) {
            log.warning("server.countdown must be a duration up to 1h (e.g. 5m), using 5m.");
            countdown = 300_000L;
        }
        String plainStop = c.getString("server.plain-stop", "stop").trim().toLowerCase(Locale.ROOT);
        if (!plainStop.equals("stop") && !plainStop.equals("restart")) {
            plainStop = "stop";
        }
        return new ServerSettings(min, max, String.join(" ", JavaFlags.allowed(flags)), update, List.copyOf(times),
                countdown, plainStop, Math.clamp(c.getInt("server.crashes.max", 3), 1, 100),
                Math.clamp(c.getInt("server.crashes.window-minutes", 15), 1, 1440),
                Math.clamp(c.getInt("server.hang-seconds", 180), 0, 3600),
                Math.clamp(c.getInt("server.stop-timeout-seconds", 180), 30, 3600),
                c.getBoolean("server.discord-announce", false));
    }
}

package at.marcelr.mradmin;

import at.marcelr.mradmin.util.DurationUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Formatting of times and durations with the patterns from the language file. */
public final class Texts {

    private Texts() {
    }

    /** "20:00" for today, otherwise "19.10.2026 20:00" (time-format / date-format); null -> maintenance-open-end. */
    public static String at(MRAdmin plugin, Instant instant) {
        Messages messages = plugin.messages();
        if (instant == null) {
            return messages.plain("time-open-end");
        }
        ZoneId zone = plugin.settings().zone();
        boolean today = instant.atZone(zone).toLocalDate().equals(LocalDate.now(zone));
        String pattern = messages.plain(today ? "time-format" : "date-format");
        try {
            return DateTimeFormatter.ofPattern(pattern).withZone(zone).format(instant);
        } catch (IllegalArgumentException e) {
            return DateTimeFormatter.ofPattern(today ? "HH:mm" : "dd.MM.yyyy HH:mm").withZone(zone).format(instant);
        }
    }

    /** Remaining time in words, e.g. "14m 30s". */
    public static String duration(long millis) {
        return DurationUtil.format(millis);
    }

    /** Remaining time as countdown, e.g. "14:30". */
    public static String clock(long millis) {
        return DurationUtil.clock(millis);
    }
}

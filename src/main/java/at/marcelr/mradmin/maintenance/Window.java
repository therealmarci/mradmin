package at.marcelr.mradmin.maintenance;

import java.time.Instant;

/**
 * One maintenance time window.
 *
 * @param source  where it comes from ({@link #KUMA} or {@link #MANUAL})
 * @param id      maintenance id in Uptime Kuma (0 for manual ones)
 * @param title   title shown to players (may be empty)
 * @param start   begin; null if it is already running and the begin is unknown
 * @param end     end; null if open-ended (until someone ends it)
 * @param running Uptime Kuma reports it as running right now ("under-maintenance")
 */
public record Window(String source, int id, String title, Instant start, Instant end, boolean running) {

    public static final String KUMA = "kuma";
    public static final String MANUAL = "manual";

    /** Identifies the maintenance across updates (times may change, the key does not). */
    public String key() {
        return source + ":" + id;
    }

    /** Whether the window is running at {@code now}: reported as running, or its begin has passed and its end not. */
    public boolean activeAt(Instant now) {
        if (end != null && !now.isBefore(end)) {
            return false;
        }
        return running || start == null || !now.isBefore(start);
    }

    /** Whether the window still lies ahead at {@code now}. */
    public boolean upcomingAt(Instant now) {
        return !activeAt(now) && start != null && now.isBefore(start);
    }
}

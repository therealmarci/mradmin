package at.marcelr.mradmin.api;

/**
 * The next restart or stop of the server, as MRadmin knows it ({@link MRadminService#nextRestart()}, since API 14): a
 * running countdown (started by the team, the console, mrserver or a planned restart) or else the next planned restart
 * ({@code server.restart-times}, not while planned restarts are paused).
 *
 * @param at        when the server goes down (epoch ms)
 * @param restart   true = it comes back, false = it stays down (a stop)
 * @param countdown true = the countdown already runs (players are being warned); false = only planned, it can still
 *                  be paused or changed
 * @param planned   true = a planned restart (from the restart times), false = started by someone
 * @param reason    why, in the server language (may be empty)
 */
public record RestartInfo(long at, boolean restart, boolean countdown, boolean planned, String reason) {

    /** Milliseconds until the server goes down (0 if it is already due). */
    public long millisLeft() {
        return Math.max(0, at - System.currentTimeMillis());
    }
}

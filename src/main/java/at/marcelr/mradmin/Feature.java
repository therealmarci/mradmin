package at.marcelr.mradmin;

/**
 * The features of MRadmin that can be switched off under {@code features} in config.yml. A switched-off feature
 * disappears completely: its commands answer "switched off", its buttons and tiles are gone from the menus, and its
 * listeners and timers do nothing. Its data (tickets, maintenance plans, saved admin modes ...) is kept.
 * <p>
 * Discord logs, Discord announcements, Uptime Kuma and the update check keep their own {@code enabled} switch in their
 * sections; the integrations with MRsleep/MRchunks are under {@code integrations}.
 */
public enum Feature {
    /** Maintenance mode with countdown, bossbar, MOTD and the Uptime Kuma connection. */
    MAINTENANCE("maintenance"),
    /** /mradmin announce and the announcement menu. */
    ANNOUNCE("announce"),
    /** /mradmin mode and the admin compass. */
    ADMIN_MODE("admin-mode"),
    /** Teleporting to players (player list, player menu, /mradmin tp, server health). */
    TELEPORT("teleport"),
    /** Summoning a player to oneself. */
    SUMMON("summon"),
    /** Viewing inventory and ender chest of other players. */
    INVENTORY("inventory"),
    /** Freezing players. */
    FREEZE("freeze"),
    /** Server health (/mradmin status and its tile). */
    SERVER_STATUS("server-status"),
    /** Tickets and reports (/report, /mradmin tickets). */
    TICKETS("tickets"),
    /** Staff chat (/sc). */
    STAFF_CHAT("staff-chat");

    private final String key;

    Feature(String key) {
        this.key = key;
    }

    /** Name under {@code features} in config.yml. */
    public String key() {
        return key;
    }
}

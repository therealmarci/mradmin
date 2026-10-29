package at.marcelr.mradmin;

/**
 * Every permission of MRadmin in one place. One permission per function, valid in game and in the web interface
 * alike - whoever may only read tickets in game may only read them in the web. The plugin.yml bundles them
 * (mradmin.support, mradmin.staff, mradmin.admin).
 */
public final class Perms {

    // dashboard and server
    public static final String DASHBOARD = "mradmin.dashboard";
    public static final String STATUS = "mradmin.status";
    public static final String STATUS_HEALTH = "mradmin.status.health";

    // tickets
    public static final String TICKETS_CREATE = "mradmin.tickets.create";
    public static final String TICKETS_NOTIFY = "mradmin.tickets.notify";
    public static final String TICKETS_VIEW = "mradmin.tickets.view";
    public static final String TICKETS_REPLY = "mradmin.tickets.reply";
    public static final String TICKETS_CLAIM = "mradmin.tickets.claim";
    public static final String TICKETS_CLOSE = "mradmin.tickets.close";
    public static final String TICKETS_REOPEN = "mradmin.tickets.reopen";
    public static final String TICKETS_TELEPORT = "mradmin.tickets.teleport";

    // maintenance
    public static final String MAINTENANCE_VIEW = "mradmin.maintenance.view";
    public static final String MAINTENANCE_MANAGE = "mradmin.maintenance.manage";
    public static final String MAINTENANCE_BYPASS = "mradmin.maintenance.bypass";
    public static final String MAINTENANCE_NOTIFY = "mradmin.maintenance.notify";

    // announcements, admin mode, staff chat
    public static final String ANNOUNCE = "mradmin.announce";
    public static final String ANNOUNCE_TEMPLATES = "mradmin.announce.templates";
    public static final String NEWS = "mradmin.news";
    public static final String MODE = "mradmin.mode";
    public static final String MODE_OTHERS = "mradmin.mode.others";
    public static final String STAFFCHAT = "mradmin.staffchat";

    // players
    public static final String PLAYER_VIEW = "mradmin.player.view";
    public static final String PLAYER_TELEPORT = "mradmin.player.teleport";
    public static final String PLAYER_SUMMON = "mradmin.player.summon";
    public static final String PLAYER_INVENTORY = "mradmin.player.inventory";
    public static final String PLAYER_FREEZE = "mradmin.player.freeze";
    public static final String PLAYER_KICK = "mradmin.player.kick";

    // web interface
    public static final String WEB = "mradmin.web";
    /** /web: the player's own page in the web interface (player area). */
    public static final String WEB_SELF = "mradmin.web.self";
    public static final String WEB_AUDIT = "mradmin.web.audit";
    public static final String WEB_CONSOLE_VIEW = "mradmin.web.console.view";
    public static final String WEB_CONSOLE_EXECUTE = "mradmin.web.console.execute";
    public static final String WEB_MANAGE = "mradmin.web.manage";

    // administration
    public static final String ADMIN_RELOAD = "mradmin.admin.reload";
    public static final String ADMIN_STORAGE = "mradmin.admin.storage";
    public static final String ADMIN_DISCORD = "mradmin.admin.discord";
    public static final String ADMIN_UPDATE = "mradmin.admin.update";

    // versions and restart (web interface)
    public static final String VERSIONS_VIEW = "mradmin.versions.view";
    public static final String VERSIONS_INSTALL = "mradmin.versions.install";
    public static final String SERVER_RESTART = "mradmin.server.restart";

    private Perms() {
    }
}

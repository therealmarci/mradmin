package at.marcelr.mradmin;

import at.marcelr.mradmin.util.DurationUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/** All values from config.yml, parsed and validated once per (re)load. */
public record Settings(
        ZoneId zone,
        MaintenanceSettings maintenance,
        KumaSettings kuma,
        AnnounceSettings announce,
        AdminModeSettings adminMode,
        UpdateSettings update,
        GuiSettings gui,
        TicketSettings tickets,
        Set<Feature> features,
        StorageSettings storage,
        WebSettings web
) {

    /**
     * The web interface. {@code publicUrl} without trailing slash (empty = not set up yet); {@code roles} maps a role to
     * its permissions and {@code players} a player (name or UUID, lower case) to a role - only used without LuckPerms.
     */
    public record WebSettings(boolean enabled, String bind, int port, String publicUrl, Set<String> trustedProxies,
                              int sessionDays, boolean passkeys, boolean password,
                              java.util.Map<String, List<String>> roles, java.util.Map<String, String> players,
                              String imprintUrl, String privacyUrl, long auditRetention, boolean maskIp) {

        /** Cookies only over HTTPS when the public address is HTTPS (plain http is only for local tests). */
        public boolean secure() {
            return publicUrl.startsWith("https://");
        }
    }

    /**
     * Where the data is kept: {@code type} is "yaml", "sqlite" or "mysql" (MySQL/MariaDB). Changes need a restart.
     *
     * @param serverId    stored with every row, so several servers can share a database later
     * @param tablePrefix in front of every table name (letters, digits, _)
     */
    public record StorageSettings(String type, String host, int port, String database, String username, String password,
                                  int poolSize, String serverId, String tablePrefix) {
    }

    /** Whether a feature is switched on under {@code features} (all are by default). */
    public boolean enabled(Feature feature) {
        return features.contains(feature);
    }

    /** Durations in ms; {@code reminders} sorted from the earliest (largest) to the last one. {@code sound} may be null. */
    public record MaintenanceSettings(long warnBefore, boolean bossbar, BossBar.Color color, BossBar.Color finalColor,
                                      int finalSeconds, BossBar.Overlay overlay, boolean showDuring,
                                      BossBar.Color duringColor, List<Long> reminders, int titleCountdownSeconds,
                                      Key sound, boolean kickOnStart, int kickDelaySeconds, boolean motd,
                                      boolean replaceVersion) {
    }

    /** {@code url} without trailing slash; empty id sets mean "every maintenance". */
    public record KumaSettings(boolean enabled, String url, String username, String password, String token,
                               Set<Integer> monitorIds, Set<Integer> maintenanceIds, int refreshSeconds,
                               int reconnectSeconds, boolean keepWhenOffline) {

        public boolean hasLogin() {
            return !token.isEmpty() || (!username.isEmpty() && !password.isEmpty());
        }
    }

    /** Title times in ticks; {@code sound} may be null. */
    public record AnnounceSettings(int fadeIn, int stay, int fadeOut, boolean chat, Key sound, float volume,
                                   float pitch) {
    }

    /** {@code buttons} = plugin buttons of the compass menu, in config order. */
    public record AdminModeSettings(GameMode gameMode, boolean returnToStart, Material compass, int compassSlot,
                                    boolean preventDrop, boolean preventPickup, boolean endOnQuit,
                                    List<HubButton> buttons) {
    }

    /**
     * A button in the compass menu that runs a command as the player, e.g. "mrchunks gui".
     *
     * @param plugin     only shown if this plugin is installed and enabled ("" = always)
     * @param permission only shown with this permission ("" = everyone in admin mode)
     * @param hook       optional class of the plugin for live information and its player menu (see PluginHooks)
     */
    public record HubButton(String id, String plugin, Material material, String command, String permission,
                            String hook) {
    }

    public record UpdateSettings(boolean enabled, String source, boolean includePrereleases,
                                 int intervalHours, boolean notifyOnJoin) {
    }

    public record GuiSettings(int refreshTicks, boolean sounds) {
    }

    /**
     * Durations in ms. {@code sound} (may be null) plays for notifications about new tickets and answers.
     *
     * @param icon       item of every ticket in the menus
     * @param categories the categories offered when creating a ticket, in config order (never empty)
     * @param chunkloaderLocks open a ticket for the owner when MRchunks locks a chunk loader
     */
    public record TicketSettings(long cooldown, long replyCooldown, int maxOpenPerPlayer,
                                 int minSubjectLength, int maxSubjectLength, int minTextLength, int maxTextLength,
                                 boolean notifyCreator, boolean showStaffNames, boolean autoClaim, int shownMessages,
                                 long keepClosed, Key sound, Material icon, Material teamIcon,
                                 List<TicketCategory> categories, boolean chunkloaderLocks) {

        /** The category with this id, or null if it is not (or no longer) configured. */
        public TicketCategory category(String id) {
            return categories.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
        }
    }

    /**
     * A ticket category from tickets.categories.
     *
     * @param name       display name; empty = text from the language file (ticket-categories.&lt;id&gt;)
     * @param player     a player report: asks which player it is about
     * @param permission needed to choose it ("" = everyone who may open tickets)
     */
    public record TicketCategory(String id, String name, boolean player, String permission) {
    }

    public static Settings from(FileConfiguration c, Logger log) {
        List<Long> reminders = new ArrayList<>();
        for (String raw : c.getStringList("maintenance.reminders")) {
            long ms = DurationUtil.parse(raw);
            if (ms > 0) {
                reminders.add(ms);
            } else {
                log.warning("maintenance.reminders: '" + raw + "' is not a valid duration, ignored.");
            }
        }
        reminders.sort(Comparator.reverseOrder());
        MaintenanceSettings maintenance = new MaintenanceSettings(
                duration(c, "maintenance.warn-before", 15 * 60_000L, log),
                c.getBoolean("maintenance.bossbar.enabled", true),
                parseEnum(c.getString("maintenance.bossbar.color"), BossBar.Color.YELLOW, "maintenance.bossbar.color", log),
                parseEnum(c.getString("maintenance.bossbar.final-color"), BossBar.Color.RED, "maintenance.bossbar.final-color", log),
                Math.max(0, c.getInt("maintenance.bossbar.final-seconds", 60)),
                parseEnum(c.getString("maintenance.bossbar.overlay"), BossBar.Overlay.NOTCHED_10, "maintenance.bossbar.overlay", log),
                c.getBoolean("maintenance.bossbar.show-during-maintenance", true),
                parseEnum(c.getString("maintenance.bossbar.during-color"), BossBar.Color.PURPLE, "maintenance.bossbar.during-color", log),
                List.copyOf(reminders),
                Math.clamp(c.getInt("maintenance.title-countdown-seconds", 10), 0, 60),
                sound(c.getString("maintenance.sound", ""), "maintenance.sound", log),
                c.getBoolean("maintenance.kick-on-start", true),
                Math.clamp(c.getInt("maintenance.kick-delay-seconds", 5), 0, 300),
                c.getBoolean("maintenance.motd.enabled", true),
                c.getBoolean("maintenance.motd.replace-version", true));

        String url = c.getString("uptime-kuma.url", "").trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        KumaSettings kuma = new KumaSettings(
                c.getBoolean("uptime-kuma.enabled", false),
                url,
                c.getString("uptime-kuma.username", "").trim(),
                c.getString("uptime-kuma.password", ""),
                c.getString("uptime-kuma.token", "").trim(),
                ids(c.getStringList("uptime-kuma.monitor-ids"), "uptime-kuma.monitor-ids", log),
                ids(c.getStringList("uptime-kuma.maintenance-ids"), "uptime-kuma.maintenance-ids", log),
                Math.clamp(c.getInt("uptime-kuma.refresh-seconds", 60), 10, 3600),
                Math.clamp(c.getInt("uptime-kuma.reconnect-seconds", 30), 5, 3600),
                c.getBoolean("uptime-kuma.keep-schedule-when-offline", true));

        AnnounceSettings announce = new AnnounceSettings(
                ticks(c.getDouble("announce.fade-in", 0.5)),
                Math.max(1, ticks(c.getDouble("announce.stay", 6))),
                ticks(c.getDouble("announce.fade-out", 1)),
                c.getBoolean("announce.chat", true),
                sound(c.getString("announce.sound", ""), "announce.sound", log),
                (float) Math.clamp(c.getDouble("announce.volume", 1.0), 0.0, 10.0),
                (float) Math.clamp(c.getDouble("announce.pitch", 1.0), 0.5, 2.0));

        GameMode gameMode = parseEnum(c.getString("admin-mode.gamemode"), GameMode.CREATIVE, "admin-mode.gamemode", log);
        if (gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR) {
            log.warning("admin-mode.gamemode must be CREATIVE or SPECTATOR, using CREATIVE.");
            gameMode = GameMode.CREATIVE;
        }
        Material compass = Material.matchMaterial(c.getString("admin-mode.compass-material", "COMPASS"));
        if (compass == null || !compass.isItem() || compass.isAir()) {
            log.warning("admin-mode.compass-material '" + c.getString("admin-mode.compass-material")
                    + "' is not an item, using COMPASS.");
            compass = Material.COMPASS;
        }
        AdminModeSettings adminMode = new AdminModeSettings(
                gameMode,
                c.getBoolean("admin-mode.return-to-start", true),
                compass,
                Math.clamp(c.getInt("admin-mode.compass-slot", 4), 0, 8),
                c.getBoolean("admin-mode.prevent-drop", true),
                c.getBoolean("admin-mode.prevent-pickup", true),
                c.getBoolean("admin-mode.end-on-quit", false),
                hubButtons(c, log));

        String source = c.getString("update-checker.source", "github").toLowerCase(Locale.ROOT);
        if (!source.equals("github") && !source.equals("modrinth")) {
            log.warning("update-checker.source '" + source + "' is unknown, using github.");
            source = "github";
        }
        UpdateSettings update = new UpdateSettings(
                c.getBoolean("update-checker.enabled", true),
                source,
                c.getBoolean("update-checker.include-prereleases", false),
                Math.max(1, c.getInt("update-checker.interval-hours", 6)),
                c.getBoolean("update-checker.notify-on-join", true));

        GuiSettings gui = new GuiSettings(
                Math.clamp(c.getInt("gui.refresh-ticks", 20), 5, 200),
                c.getBoolean("gui.sounds", true));

        String zoneName = c.getString("timezone", "").trim();
        ZoneId zone = ZoneId.systemDefault();
        if (!zoneName.isEmpty()) {
            try {
                zone = ZoneId.of(zoneName);
            } catch (DateTimeException e) {
                log.warning("timezone '" + zoneName + "' is unknown, using the server timezone " + zone + ".");
            }
        }

        int minSubject = Math.clamp(c.getInt("tickets.min-subject-length", 3), 1, 50);
        int minText = Math.clamp(c.getInt("tickets.min-text-length", 10), 0, 500);
        TicketSettings tickets = new TicketSettings(
                duration(c, "tickets.cooldown", 60_000L, log),
                duration(c, "tickets.reply-cooldown", 3_000L, log),
                Math.clamp(c.getInt("tickets.max-open-per-player", 3), 1, 100),
                minSubject,
                Math.clamp(c.getInt("tickets.max-subject-length", 50), minSubject, 100),
                minText,
                Math.clamp(c.getInt("tickets.max-text-length", 500), Math.max(10, minText), 2000),
                c.getBoolean("tickets.notify-creator", true),
                c.getBoolean("tickets.show-staff-names", true),
                c.getBoolean("tickets.auto-claim", true),
                Math.clamp(c.getInt("tickets.shown-messages", 20), 1, 100),
                duration(c, "tickets.keep-closed", 7 * 86_400_000L, log),
                sound(c.getString("tickets.sound", ""), "tickets.sound", log),
                item(c, "tickets.icon", Material.NAME_TAG, log),
                item(c, "tickets.team-icon", Material.NETHER_STAR, log),
                ticketCategories(c, log),
                c.getBoolean("tickets.chunkloader-locks", true));

        Set<Feature> features = java.util.EnumSet.noneOf(Feature.class);
        for (Feature feature : Feature.values()) {
            if (c.getBoolean("features." + feature.key(), true)) {
                features.add(feature);
            }
        }

        return new Settings(zone, maintenance, kuma, announce, adminMode, update, gui, tickets, Set.copyOf(features),
                storage(c, log), web(c, log));
    }

    private static WebSettings web(FileConfiguration c, Logger log) {
        String url = c.getString("web.public-url", "").trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.isEmpty() && !url.startsWith("https://") && !url.startsWith("http://")) {
            log.warning("web.public-url must start with https:// (e.g. https://admin.yourserver.com), ignored.");
            url = "";
        }
        java.util.Map<String, List<String>> roles = new java.util.LinkedHashMap<>();
        var roleSection = c.getConfigurationSection("web.roles");
        if (roleSection != null) {
            for (String role : roleSection.getKeys(false)) {
                roles.put(role.toLowerCase(Locale.ROOT), List.copyOf(c.getStringList("web.roles." + role)));
            }
        }
        java.util.Map<String, String> players = new java.util.HashMap<>();
        var playerSection = c.getConfigurationSection("web.players");
        if (playerSection != null) {
            for (String player : playerSection.getKeys(false)) {
                players.put(player.toLowerCase(Locale.ROOT), c.getString("web.players." + player, "").toLowerCase(Locale.ROOT));
            }
        }
        return new WebSettings(
                c.getBoolean("web.enabled", false),
                c.getString("web.bind", "127.0.0.1").trim(),
                Math.clamp(c.getInt("web.port", 8125), 1, 65535),
                url,
                Set.copyOf(c.getStringList("web.trusted-proxies")),
                Math.clamp(c.getInt("web.session-days", 30), 1, 365),
                c.getBoolean("web.login.passkeys", true),
                c.getBoolean("web.login.password", true),
                java.util.Map.copyOf(roles),
                java.util.Map.copyOf(players),
                c.getString("web.legal.imprint-url", "").trim(),
                c.getString("web.legal.privacy-url", "").trim(),
                duration(c, "web.privacy.audit-retention", 90 * 86_400_000L, log),
                c.getBoolean("web.privacy.mask-ip", false));
    }

    private static StorageSettings storage(FileConfiguration c, Logger log) {
        String type = c.getString("storage.type", "yaml").trim().toLowerCase(Locale.ROOT);
        if (type.equals("mariadb")) {
            type = "mysql";
        }
        if (!type.equals("yaml") && !type.equals("sqlite") && !type.equals("mysql")) {
            log.warning("storage.type '" + type + "' is unknown (yaml, sqlite, mysql), using yaml.");
            type = "yaml";
        }
        String prefix = c.getString("storage.table-prefix", "mradmin_").trim();
        if (!prefix.matches("[A-Za-z0-9_]{0,32}")) {
            log.warning("storage.table-prefix may only contain letters, digits and _, using mradmin_.");
            prefix = "mradmin_";
        }
        String serverId = c.getString("storage.server-id", "main").trim();
        if (serverId.isEmpty() || serverId.length() > 64) {
            serverId = "main";
        }
        return new StorageSettings(type,
                c.getString("storage.mysql.host", "localhost").trim(),
                Math.clamp(c.getInt("storage.mysql.port", 3306), 1, 65535),
                c.getString("storage.mysql.database", "mradmin").trim(),
                c.getString("storage.mysql.username", ""),
                c.getString("storage.mysql.password", ""),
                Math.clamp(c.getInt("storage.mysql.pool-size", 4), 1, 20),
                serverId,
                prefix);
    }

    private static Material item(FileConfiguration c, String path, Material fallback, Logger log) {
        Material item = Material.matchMaterial(c.getString(path, fallback.name()));
        if (item == null || !item.isItem() || item.isAir()) {
            log.warning(path + " '" + c.getString(path) + "' is not an item, using " + fallback + ".");
            return fallback;
        }
        return item;
    }

    private static List<TicketCategory> ticketCategories(FileConfiguration c, Logger log) {
        List<TicketCategory> categories = new ArrayList<>();
        var section = c.getConfigurationSection("tickets.categories");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String path = "tickets.categories." + key;
                if (!c.isConfigurationSection(path)) {
                    log.warning(path + " is not a section (name, player ...), ignored.");
                    continue;
                }
                if (!c.getBoolean(path + ".enabled", true)) {
                    continue;
                }
                categories.add(new TicketCategory(key.toLowerCase(Locale.ROOT), c.getString(path + ".name", "").trim(),
                        c.getBoolean(path + ".player", false), c.getString(path + ".permission", "").trim()));
            }
        }
        if (categories.isEmpty()) {
            log.warning("tickets.categories is empty, using a single category 'other'.");
            categories.add(new TicketCategory("other", "", false, ""));
        }
        return List.copyOf(categories);
    }

    /**
     * The built-in integrations that are switched on (they only show while their plugin runs, see
     * {@link at.marcelr.mradmin.hook.Integration}), followed by the owner's own buttons - at most 6 together.
     */
    private static List<HubButton> hubButtons(FileConfiguration c, Logger log) {
        List<HubButton> buttons = new ArrayList<>();
        Set<String> builtInHooks = new java.util.HashSet<>();
        for (var integration : at.marcelr.mradmin.hook.Integration.ALL) {
            builtInHooks.add(integration.hook());
            if (c.getBoolean("integrations." + integration.id(), true)) {
                buttons.add(new HubButton(integration.id(), integration.plugin(), integration.material(),
                        integration.command(), integration.permission(), integration.hook()));
            }
        }
        var section = c.getConfigurationSection("admin-mode.menu.buttons");
        if (section == null) {
            return List.copyOf(buttons);
        }
        for (String id : section.getKeys(false)) {
            String path = "admin-mode.menu.buttons." + id;
            if (!c.getBoolean(path + ".enabled", true)) {
                continue;
            }
            if (builtInHooks.contains(c.getString(path + ".hook", "").trim())) {
                // Old config: MRsleep/MRchunks are built in now (integrations), no second button.
                continue;
            }
            String command = c.getString(path + ".command", "").trim();
            while (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (command.isEmpty()) {
                log.warning(path + ".command is empty, button ignored.");
                continue;
            }
            Material material = Material.matchMaterial(c.getString(path + ".material", "COMMAND_BLOCK"));
            if (material == null || !material.isItem() || material.isAir()) {
                log.warning(path + ".material '" + c.getString(path + ".material") + "' is not an item, using COMMAND_BLOCK.");
                material = Material.COMMAND_BLOCK;
            }
            if (buttons.size() == 6) {
                log.warning("admin-mode.menu.buttons: only 6 buttons fit into the menu, '" + id + "' and the rest are ignored.");
                break;
            }
            buttons.add(new HubButton(id, c.getString(path + ".plugin", "").trim(), material, command,
                    c.getString(path + ".permission", "").trim(), c.getString(path + ".hook", "").trim()));
        }
        return List.copyOf(buttons);
    }

    private static long duration(FileConfiguration c, String path, long fallback, Logger log) {
        String raw = c.getString(path, "");
        long ms = DurationUtil.parse(raw);
        if (ms < 0) {
            log.warning(path + " '" + raw + "' is not a valid duration, using " + DurationUtil.format(fallback) + ".");
            return fallback;
        }
        return ms;
    }

    private static int ticks(double seconds) {
        return (int) Math.round(Math.clamp(seconds, 0, 600) * 20);
    }

    /** Numbers from a list like {@code [12, 15]} (read as strings, so "12" and 12 both work). */
    private static Set<Integer> ids(List<String> raw, String path, Logger log) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (String value : raw) {
            try {
                ids.add(Integer.parseInt(value.trim()));
            } catch (NumberFormatException e) {
                log.warning(path + ": '" + value + "' is not a number, ignored.");
            }
        }
        return Set.copyOf(ids);
    }

    /**
     * A sound from its Bukkit name ("BLOCK_NOTE_BLOCK_PLING") or key ("minecraft:block.note_block.pling",
     * also for sounds of resource packs). Empty = no sound (null).
     */
    static Key sound(String raw, String path, Logger log) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.contains(".") || value.contains(":")) {
            try {
                return Key.key(value.toLowerCase(Locale.ROOT));
            } catch (InvalidKeyException e) {
                log.warning(path + " '" + raw + "' is not a valid sound key, no sound.");
                return null;
            }
        }
        try {
            Object sound = org.bukkit.Sound.class.getField(value.toUpperCase(Locale.ROOT)).get(null);
            return org.bukkit.Registry.SOUND_EVENT.getKey((org.bukkit.Sound) sound);
        } catch (ReflectiveOperationException | ClassCastException e) {
            log.warning(path + " '" + raw + "' is not a known sound, no sound.");
            return null;
        }
    }

    static <E extends Enum<E>> E parseEnum(String raw, E fallback, String path, Logger log) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            log.warning(path + " '" + raw + "' is invalid, using " + fallback.name().toLowerCase(Locale.ROOT) + ".");
            return fallback;
        }
    }
}

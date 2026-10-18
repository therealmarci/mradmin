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
        ReportSettings reports
) {

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

    /** Durations in ms. */
    public record ReportSettings(long cooldown, int minReasonLength, int maxReasonLength, int maxOpenPerPlayer,
                                 boolean tellReporter, long keepClosed) {
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

        ReportSettings reports = new ReportSettings(
                duration(c, "reports.cooldown", 60_000L, log),
                Math.clamp(c.getInt("reports.min-reason-length", 3), 0, 100),
                Math.clamp(c.getInt("reports.max-reason-length", 200), 10, 1000),
                Math.clamp(c.getInt("reports.max-open-per-player", 3), 1, 100),
                c.getBoolean("reports.tell-reporter", true),
                duration(c, "reports.keep-closed", 7 * 86_400_000L, log));

        return new Settings(zone, maintenance, kuma, announce, adminMode, update, gui, reports);
    }

    private static List<HubButton> hubButtons(FileConfiguration c, Logger log) {
        List<HubButton> buttons = new ArrayList<>();
        var section = c.getConfigurationSection("admin-mode.menu.buttons");
        if (section == null) {
            return List.of();
        }
        for (String id : section.getKeys(false)) {
            String path = "admin-mode.menu.buttons." + id;
            if (!c.getBoolean(path + ".enabled", true)) {
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

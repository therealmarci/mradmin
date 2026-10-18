package at.marcelr.mradmin;

import at.marcelr.mradmin.announce.Announcer;
import at.marcelr.mradmin.command.MRAdminCommand;
import at.marcelr.mradmin.command.ReportCommand;
import at.marcelr.mradmin.command.StaffChat;
import at.marcelr.mradmin.hook.PluginHooks;
import at.marcelr.mradmin.player.FreezeListener;
import at.marcelr.mradmin.player.FreezeManager;
import at.marcelr.mradmin.report.ReportManager;
import at.marcelr.mradmin.discord.DiscordAnnouncer;
import at.marcelr.mradmin.gui.MenuListener;
import at.marcelr.mradmin.maintenance.MaintenanceListener;
import at.marcelr.mradmin.maintenance.MaintenanceManager;
import at.marcelr.mradmin.mode.AdminModeListener;
import at.marcelr.mradmin.mode.AdminModeManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MRAdmin extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private Settings settings;
    /**
     * lang-version 2: maintenance topic as its own line, MOTD with countdown; 3: Discord announcements (help, usage);
     * 4: MOTD colours, countdown with hours; 5: compass opens the admin menu (hub);
     * 6: player menu, reports, staff chat, server health; 7: help text of the admin mode;
     * 8: dashboard (/mradmin opens it).
     * Raise it and list the keys whenever a default text changes.
     */
    private final Messages messages = new Messages(8, Map.of(
            2, Set.of("maintenance-title-line", "maintenance-announced", "maintenance-started", "maintenance-kick",
                    "maintenance-motd"),
            3, Set.of("help-discord", "announce-usage", "discord-test-hint", "announce-presets.update.subtitle",
                    "announce-presets.update.chat"),
            4, Set.of("maintenance-motd", "maintenance-motd-open"),
            5, Set.of("mode-on", "mode-compass-name", "mode-compass-lore"),
            6, Set.of("gui-teleport-player-lore"),
            7, Set.of("help-mode"),
            8, Set.of("help-menu", "gui-title-hub")));
    private final DiscordWebhook discord = new DiscordWebhook(this);
    private DiscordAnnouncer discordAnnounce;
    private final Announcer announcer = new Announcer(this);
    private MaintenanceManager maintenance;
    private AdminModeManager adminMode;
    private UpdateChecker updateChecker;
    private FreezeManager freeze;
    private ReportManager reports;
    private PluginHooks hooks;
    private StaffChat staffChat;
    private MenuListener menuListener;

    @Override
    public void onEnable() {
        discordAnnounce = new DiscordAnnouncer(this);
        ConfigUpdater.update(this);
        loadConfiguration();

        freeze = new FreezeManager(this);
        freeze.load();
        reports = new ReportManager(this);
        reports.load();
        hooks = new PluginHooks(this);
        staffChat = new StaffChat(this);
        maintenance = new MaintenanceManager(this);
        discordAnnounce.loadState();
        maintenance.load();
        adminMode = new AdminModeManager(this);
        updateChecker = new UpdateChecker(this);
        menuListener = new MenuListener(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MaintenanceListener(this), this);
        pm.registerEvents(new AdminModeListener(this), this);
        pm.registerEvents(menuListener, this);
        pm.registerEvents(this, this);
        pm.registerEvents(new FreezeListener(this), this);
        pm.registerEvents(staffChat, this);

        PluginCommand command = Objects.requireNonNull(getCommand("mradmin"), "mradmin");
        MRAdminCommand executor = new MRAdminCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        register("report", new ReportCommand(this));
        register("staffchat", staffChat);

        startServices();
        if (pm.getPlugin("LuckPerms") == null) {
            getLogger().info("LuckPerms not found - during maintenance, players without permission are kicked right after joining.");
        }
    }

    @Override
    public void onDisable() {
        if (menuListener != null) {
            menuListener.stop();
            menuListener.closeAll();
        }
        if (maintenance != null) {
            maintenance.stop();
        }
        if (adminMode != null) {
            adminMode.stop();
        }
        if (freeze != null) {
            freeze.stop();
        }
        if (updateChecker != null) {
            updateChecker.stop();
        }
        discord.shutdown();
        if (discordAnnounce != null) {
            discordAnnounce.shutdown();
        }
    }

    /**
     * Reloads config.yml and the language file and restarts everything that depends on them. A broken config.yml is
     * not applied (the running configuration stays active), so a typo cannot switch off e.g. the maintenance mode.
     *
     * @return null on success, otherwise [problem, line] from {@link ConfigUpdater#validate}
     */
    public String[] reload() {
        String[] problem = ConfigUpdater.validate(this);
        if (problem != null) {
            getLogger().severe("config.yml is invalid near line " + problem[1] + " (" + problem[0]
                    + ") - the current configuration stays active.");
            return problem;
        }
        ConfigUpdater.update(this);
        loadConfiguration();
        startServices();
        return null;
    }

    private void startServices() {
        hooks.clear();
        freeze.start();
        maintenance.start();
        adminMode.start();
        updateChecker.start();
        menuListener.start();
    }

    private void loadConfiguration() {
        config = ConfigUpdater.load(this);
        messages.load(this, config.getString("language", "de"));
        settings = Settings.from(config, getLogger());
        discord.load(config.getConfigurationSection("discord"));
        discordAnnounce.load(config.getConfigurationSection("discord-announce"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        updateChecker.notifyIfOutdated(event.getPlayer());
        reports.notifyOnJoin(event.getPlayer());
    }

    @Override
    public FileConfiguration getConfig() {
        if (config == null) {
            config = ConfigUpdater.load(this);
        }
        return config;
    }

    @Override
    public void reloadConfig() {
        config = ConfigUpdater.load(this);
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public DiscordWebhook discord() {
        return discord;
    }

    public DiscordAnnouncer discordAnnounce() {
        return discordAnnounce;
    }

    public Announcer announcer() {
        return announcer;
    }

    public MaintenanceManager maintenance() {
        return maintenance;
    }

    public AdminModeManager adminMode() {
        return adminMode;
    }

    public FreezeManager freeze() {
        return freeze;
    }

    public ReportManager reports() {
        return reports;
    }

    public PluginHooks hooks() {
        return hooks;
    }

    public StaffChat staffChat() {
        return staffChat;
    }

    private void register(String name, org.bukkit.command.TabExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), name);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public UpdateChecker updateChecker() {
        return updateChecker;
    }
}

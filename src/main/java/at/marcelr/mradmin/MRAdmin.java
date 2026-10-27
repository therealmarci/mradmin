package at.marcelr.mradmin;

import at.marcelr.mradmin.announce.Announcer;
import at.marcelr.mradmin.command.MRAdminCommand;
import at.marcelr.mradmin.command.ReportCommand;
import at.marcelr.mradmin.command.StaffChat;
import at.marcelr.mradmin.module.Modules;
import at.marcelr.mradmin.player.FreezeListener;
import at.marcelr.mradmin.player.FreezeManager;
import at.marcelr.mradmin.player.Npcs;
import at.marcelr.mradmin.ticket.TicketManager;
import at.marcelr.mradmin.discord.DiscordAnnouncer;
import at.marcelr.mradmin.gui.MenuListener;
import at.marcelr.mradmin.maintenance.MaintenanceListener;
import at.marcelr.mradmin.maintenance.MaintenanceManager;
import at.marcelr.mradmin.mode.AdminModeListener;
import at.marcelr.mradmin.mode.CreativeClearGuard;
import at.marcelr.mradmin.mode.AdminModeManager;
import at.marcelr.mradmin.hook.Integration;
import at.marcelr.mradmin.storage.Storage;
import at.marcelr.mradmin.health.HealthMonitor;
import at.marcelr.mradmin.web.AuthRoutes;
import at.marcelr.mradmin.web.WebServer;
import at.marcelr.mradmin.web.auth.WebAuth;
import at.marcelr.mradmin.web.auth.WebPermissions;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
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
     * 8: dashboard (/mradmin opens it); 9: ticket categories, archive (texts of the unreleased ticket system);
     * 10: shift-click hint of the player list and right-click hint of the ticket list as their own texts (hidden while teleporting is switched off).
     * Raise it and list the keys whenever a default text changes.
     */
    private final Messages messages = new Messages(13, Map.ofEntries(
            Map.entry(2, Set.of("maintenance-title-line", "maintenance-announced", "maintenance-started", "maintenance-kick",
                    "maintenance-motd")),
            Map.entry(3, Set.of("help-discord", "announce-usage", "discord-test-hint", "announce-presets.update.subtitle",
                    "announce-presets.update.chat")),
            Map.entry(4, Set.of("maintenance-motd", "maintenance-motd-open")),
            Map.entry(5, Set.of("mode-on", "mode-compass-name", "mode-compass-lore")),
            Map.entry(6, Set.of("gui-teleport-player-lore")),
            Map.entry(7, Set.of("help-mode")),
            Map.entry(8, Set.of("help-menu", "gui-title-hub")),
            Map.entry(9, Set.of("ticket-categories.cheating", "ticket-categories.chat", "ticket-categories.griefing",
                    "ticket-categories.scam", "ticket-categories.other", "gui-my-tickets-head-lore", "gui-my-tickets-empty",
                    "gui-my-tickets-empty-lore", "gui-ticket-lore", "gui-ticket-lore-staff", "gui-tickets-filter-type",
                    "gui-tickets-filter-type-lore", "ticket-form-player-hint", "ticket-form-other-hint",
                    "ticket-chat-header-player", "ticket-chat-header-other", "gui-new-ticket-none")),
            Map.entry(10, Set.of("gui-teleport-player-lore", "gui-ticket-lore-staff")),
            // 11: /mradmin announce for scripts (parts and named values).
            Map.entry(11, Set.of("announce-usage")),
            // 12: "Website | GitHub" in /mradmin version.
            Map.entry(12, Set.of("version-links")),
            // 13: one Discord message per maintenance with the actual begin and end.
            Map.entry(13, Set.of("discord-announce.maintenance-started", "discord-announce.maintenance-ended"))));
    private final DiscordWebhook discord = new DiscordWebhook(this);
    private DiscordAnnouncer discordAnnounce;
    private final Announcer announcer = new Announcer(this);
    private MaintenanceManager maintenance;
    private AdminModeManager adminMode;
    private UpdateChecker updateChecker;
    private at.marcelr.mradmin.versions.VersionManager versions;
    private at.marcelr.mradmin.versions.RestartManager restarts;
    private FreezeManager freeze;
    private TicketManager tickets;
    private at.marcelr.mradmin.proxy.ProxyManager proxy;
    private Modules modules;
    private Storage storage;
    private WebServer web;
    private HealthMonitor health;
    private StaffChat staffChat;
    private MenuListener menuListener;

    @Override
    public void onEnable() {
        discordAnnounce = new DiscordAnnouncer(this);
        ConfigUpdater.update(this);
        loadConfiguration();
        storage = Storage.create(this);
        getLogger().info("Storage: " + storage.type() + " (" + storage.describe() + ").");
        WebAuth webAuth = new WebAuth(this);
        webAuth.load();
        web = new WebServer(this, webAuth, new WebPermissions(this));
        AuthRoutes.register(web);
        at.marcelr.mradmin.web.PanelRoutes.register(web);
        at.marcelr.mradmin.web.SiteRoutes.register(web);
        at.marcelr.mradmin.web.HealthRoutes.register(web);
        at.marcelr.mradmin.web.ModuleRoutes.register(web);
        at.marcelr.mradmin.web.PlayerRoutes.register(web);
        at.marcelr.mradmin.web.ConsoleRoutes.register(web);
        at.marcelr.mradmin.web.MaintenanceRoutes.register(web);
        at.marcelr.mradmin.web.VersionRoutes.register(web);
        at.marcelr.mradmin.web.PushRoutes.register(web);
        at.marcelr.mradmin.web.DiscordRoutes.register(web);
        at.marcelr.mradmin.web.PublicRoutes.register(web);
        web.push().load();
        web.requests().load();
        health = new HealthMonitor(this);

        freeze = new FreezeManager(this);
        freeze.load();
        tickets = new TicketManager(this);
        tickets.load();
        modules = new Modules(this);
        // Before the other plugins enable (they softdepend on MRadmin) - they register their modules then.
        modules.offer();
        staffChat = new StaffChat(this);
        proxy = new at.marcelr.mradmin.proxy.ProxyManager(this);
        maintenance = new MaintenanceManager(this);
        discordAnnounce.loadState();
        maintenance.load();
        adminMode = new AdminModeManager(this);
        updateChecker = new UpdateChecker(this);
        versions = new at.marcelr.mradmin.versions.VersionManager(this);
        restarts = new at.marcelr.mradmin.versions.RestartManager(this);
        menuListener = new MenuListener(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MaintenanceListener(this), this);
        pm.registerEvents(new AdminModeListener(this), this);
        pm.registerEvents(new CreativeClearGuard(this), this);
        pm.registerEvents(menuListener, this);
        pm.registerEvents(this, this);
        pm.registerEvents(new FreezeListener(this), this);
        pm.registerEvents(staffChat, this);
        pm.registerEvents(web.requests(), this);
        pm.registerEvents(proxy, this);

        PluginCommand command = Objects.requireNonNull(getCommand("mradmin"), "mradmin");
        MRAdminCommand executor = new MRAdminCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        register("report", new ReportCommand(this));
        register("staffchat", staffChat);

        startServices();
        // After all plugins are enabled: which of MRsleep/MRchunks are there.
        Bukkit.getScheduler().runTask(this, this::logIntegrations);
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
        if (versions != null) {
            versions.shutdown();
        }
        if (updateChecker != null) {
            updateChecker.stop();
        }
        discord.shutdown();
        if (discordAnnounce != null) {
            discordAnnounce.shutdown();
        }
        if (web != null) {
            web.stop();
            web.auth().flush();
        }
        if (health != null) {
            health.stop();
        }
        if (proxy != null) {
            // Main server: "stopping" for the lobby.
            proxy.stop();
        }
        // Last: everything above may still save something.
        if (storage != null) {
            storage.close();
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
        String storageBefore = settings.storage().toString();
        loadConfiguration();
        if (!settings.storage().toString().equals(storageBefore)) {
            getLogger().warning("storage in config.yml was changed - this takes effect after a server restart.");
        }
        startServices();
        return null;
    }

    private void startServices() {
        web.permissions().clear();
        web.start();
        if (feature(Feature.SERVER_STATUS)) {
            health.start();
        } else {
            health.stop();
        }
        freeze.start();
        // Before the maintenance: on the waiting lobby the maintenance follows the main server.
        proxy.start();
        maintenance.start();
        adminMode.start();
        updateChecker.start();
        versions.start();
        menuListener.start();
    }

    private void loadConfiguration() {
        config = ConfigUpdater.load(this);
        messages.load(this, config.getString("language", "auto"), config.getString("fallback-language", "en"),
                config.getString("server-language", "en"));
        settings = Settings.from(config, getLogger());
        discord.load(config.getConfigurationSection("discord"));
        discordAnnounce.load(config.getConfigurationSection("discord-announce"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (Npcs.is(event.getPlayer())) {
            return;
        }
        updateChecker.notifyIfOutdated(event.getPlayer());
        if (feature(Feature.TICKETS)) {
            tickets.notifyOnJoin(event.getPlayer());
        }
    }

    // ---------------------------------------------------------- features

    /** Whether a feature is switched on (features in config.yml). */
    public boolean feature(Feature feature) {
        return settings.enabled(feature);
    }

    /** Like {@link #feature(Feature)}, but tells the sender if it is switched off. */
    public boolean feature(CommandSender sender, Feature feature) {
        if (settings.enabled(feature)) {
            return true;
        }
        messages.send(sender, "feature-disabled");
        return false;
    }

    // ------------------------------------------------------ integrations

    /**
     * After all plugins are enabled: built-in integrations whose plugin runs but registered no module are an older
     * version from before the module API (connected modules log themselves when they register, see Modules).
     */
    private void logIntegrations() {
        for (Integration integration : Integration.ALL) {
            Plugin other = getServer().getPluginManager().getPlugin(integration.plugin());
            if (other == null || !other.isEnabled()) {
                continue;
            }
            if (!modules.enabled(integration.id())) {
                getLogger().info(integration.plugin() + " found - not connected (integrations." + integration.id() + ": false).");
            } else if (modules.module(integration.id()) == null) {
                getLogger().warning(integration.plugin() + " " + other.getPluginMeta().getVersion()
                        + " is too old for MRadmin's module API - update it to connect it.");
            }
        }
        // LibertyBans: only through its API, for the player page of the web interface.
        Plugin libertyBans = getServer().getPluginManager().getPlugin("LibertyBans");
        if (libertyBans != null && libertyBans.isEnabled()) {
            getLogger().info("LibertyBans found - " + (!getConfig().getBoolean("integrations.libertybans", true)
                    ? "not connected (integrations.libertybans: false)."
                    : at.marcelr.mradmin.hook.Bans.get(this) != null ? "connected (punishments on the player page in the web interface)."
                    : "its API could not be reached."));
        }
    }

    /** A plugin was disabled or removed: its module goes, its button disappears from the menus. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != this && modules.pluginDisabled(event.getPlugin())) {
            getLogger().info(event.getPlugin().getName() + " was disabled - disconnected.");
        }
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

    public at.marcelr.mradmin.proxy.ProxyManager proxy() {
        return proxy;
    }

    public TicketManager tickets() {
        return tickets;
    }

    public Storage storage() {
        return storage;
    }

    /**
     * A push notification to the team members who want this kind (texts "push-&lt;text&gt;-title/-body" from the
     * language file, placeholders as name/value pairs). Does nothing while the web interface is off.
     */
    public void push(String kind, String text, String url, java.util.function.Predicate<java.util.UUID> who,
                     Object... placeholders) {
        if (web == null) {
            return;
        }
        // Push notifications go to devices, not to players in the game - server language.
        web.push().notify(kind, messages().server(() -> messages().plain("push-" + text + "-title", placeholders)),
                messages().server(() -> messages().plain("push-" + text + "-body", placeholders)), url, who);
    }

    public WebServer web() {
        return web;
    }

    public HealthMonitor health() {
        return health;
    }

    /** The plugins MRadmin works with (module API and old hooks). */
    public Modules modules() {
        return modules;
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

    public at.marcelr.mradmin.versions.VersionManager versions() {
        return versions;
    }

    public at.marcelr.mradmin.versions.RestartManager restarts() {
        return restarts;
    }
}

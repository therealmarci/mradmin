package at.marcelr.mradmin.proxy;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.maintenance.Window;
import at.marcelr.mradmin.player.Npcs;
import at.marcelr.mradmin.storage.Storage;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static at.marcelr.mradmin.Messages.tag;

/**
 * Operation behind a Velocity proxy with a waiting lobby (config section proxy, needs a shared MySQL storage).
 * <p>
 * <b>Main server:</b> reports every few seconds in the database whether it is ready (and its maintenance). A stop -
 * console, /stop, the restart button, start scripts - first sends the players to the lobby (admins in admin mode are
 * disconnected, their admin mode stays saved) and then shuts down. At the start of a maintenance everyone outside the
 * admin mode goes to the lobby. Players are moved with the BungeeCord channel of Velocity ({@code Connect}).
 * <p>
 * <b>Lobby:</b> reads the reports, checks that the main server is reachable, shows waiting players why they wait and
 * sends them back gradually once the main server is ready and no maintenance runs. {@code /mradmin mode} there takes
 * an admin to the main server straight into admin mode (a short-lived entry in the database the main server reads
 * when they connect).
 */
public final class ProxyManager implements Listener {

    public static final String PERM_VIEW = "mradmin.proxy.view";
    public static final String PERM_SEND = "mradmin.proxy.send";
    /** Stays in the lobby (e.g. to build it) instead of being sent back. */
    public static final String PERM_STAY = "mradmin.proxy.stay";
    private static final String CHANNEL = "BungeeCord";
    /** A report older than this means the main server is gone. */
    private static final long FRESH_MILLIS = 15_000;
    /** After a stop, the lobby says "restarting" this long before it says "not reachable". */
    private static final long RESTART_MILLIS = 10 * 60_000L;
    private static final long SWITCH_MILLIS = 30_000;
    private static final long EVACUATE_TICKS = 200;

    /** How the main server is, as the lobby sees it. */
    public enum MainState { READY, MAINTENANCE, RESTARTING, OFFLINE }

    private final MRAdmin plugin;
    private boolean running;
    private BukkitTask task;
    private BukkitTask returnTask;
    private volatile boolean stopping;
    /** Main server: players who come from the lobby into admin mode (pre-login -> join). */
    private final Set<UUID> switching = ConcurrentHashMap.newKeySet();
    /** Lobby: the last view of the main server. */
    private MainState state = MainState.OFFLINE;
    private Window window;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Deque<UUID> returning = new ArrayDeque<>();
    private final List<String> problems = new ArrayList<>();

    public ProxyManager(MRAdmin plugin) {
        this.plugin = plugin;
    }

    private Settings.ProxySettings settings() {
        return plugin.settings().proxy();
    }

    private Messages messages() {
        return plugin.messages();
    }

    // -------------------------------------------------------------- lifecycle

    public void start() {
        stop();
        problems.clear();
        if (!settings().enabled()) {
            return;
        }
        if (!plugin.storage().shared()) {
            problem("storage", "proxy is enabled, but storage.type is not mysql - the proxy operation stays off");
            return;
        }
        check();
        running = true;
        stopping = false;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        if (lobby()) {
            task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::watch, 20L, 40L);
            returnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sendBack, 20L, 20L);
            Npcs.online().forEach(this::arrived);
            plugin.getLogger().info("Proxy operation: this is the waiting lobby (main server: " + settings().mainServer() + ").");
        } else {
            // The first report once the server is fully started, then every 5 seconds.
            task = Bukkit.getScheduler().runTaskTimer(plugin, () -> report(false), 1L, 100L);
            plugin.getLogger().info("Proxy operation: this is the main server (lobby: " + settings().lobbyServer() + ").");
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (returnTask != null) {
            returnTask.cancel();
            returnTask = null;
        }
        if (running && main()) {
            report(true);
        }
        for (Map.Entry<UUID, BossBar> entry : bars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        bars.clear();
        returning.clear();
        if (running) {
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        }
        running = false;
    }

    /** Proxy operation is on and works (shared storage). */
    public boolean active() {
        return running;
    }

    public boolean main() {
        return running && !settings().lobbyRole();
    }

    public boolean lobby() {
        return running && settings().lobbyRole();
    }

    /** Setup problems found at start, as keys (proxy-problem-<key> in the language files). */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    private void problem(String key, String console) {
        problems.add(key);
        plugin.getLogger().warning("Proxy: " + console);
    }

    /** Checks the parts of the setup MRadmin can see: Velocity forwarding and online mode. */
    private void check() {
        if (Bukkit.getOnlineMode()) {
            problem("online-mode", "online-mode is true in server.properties - behind Velocity it must be false (Velocity checks the accounts)");
        }
        File paper = new File(Bukkit.getWorldContainer(), "config/paper-global.yml");
        YamlConfiguration global = YamlConfiguration.loadConfiguration(paper);
        if (!global.getBoolean("proxies.velocity.enabled", false)) {
            problem("velocity", "proxies.velocity.enabled is not true in config/paper-global.yml - players would get wrong UUIDs");
        }
        if (settings().mainServer().isEmpty() || settings().lobbyServer().isEmpty()) {
            problem("names", "proxy.servers.main and proxy.servers.lobby must be the server names from the velocity.toml");
        }
    }

    /** Sends the player to another server of the proxy. */
    public void send(Player player, String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }

    // ------------------------------------------------------------ main server

    /** Writes this server's report: ready (or stopping) and its maintenance. */
    private void report(boolean stop) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("role", "main");
        y.set("server", settings().mainServer());
        y.set("state", stop || stopping ? "stopping" : "ready");
        var maintenance = plugin.maintenance();
        Window current = maintenance.current();
        if (current != null) {
            y.set("maintenance.active", maintenance.active());
            y.set("maintenance.title", current.title());
            y.set("maintenance.start", current.start() == null ? 0 : current.start().toEpochMilli());
            y.set("maintenance.end", current.end() == null ? 0 : current.end().toEpochMilli());
        }
        plugin.storage().putServerState(y.saveToString());
    }

    /**
     * Stops (or restarts) the server the proxy way: players to the lobby, admins in admin mode disconnected, then
     * down. Without proxy operation it stops right away.
     */
    public void shutdown(boolean restart) {
        if (!main()) {
            down(restart);
            return;
        }
        if (stopping) {
            return;
        }
        stopping = true;
        report(true);
        plugin.getLogger().info("Sending the players to the lobby before the server stops.");
        for (Player player : Npcs.online()) {
            if (plugin.adminMode().active(player)) {
                player.kick(messages().as(player, () -> messages().get("proxy-restart-admin")));
            } else {
                messages().send(player, "proxy-moving-restart");
                send(player, settings().lobbyServer());
            }
        }
        long[] waited = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, t -> {
            waited[0] += 5;
            if (Npcs.online().isEmpty() || waited[0] >= EVACUATE_TICKS) {
                t.cancel();
                down(restart);
            }
        }, 5L, 5L);
    }

    private static void down(boolean restart) {
        if (restart) {
            Bukkit.restart();
        } else {
            Bukkit.shutdown();
        }
    }

    /** Start of a maintenance: everyone outside the admin mode goes to the lobby. */
    public void maintenanceStarted() {
        for (Player player : Npcs.online()) {
            if (!plugin.adminMode().active(player)) {
                messages().send(player, "proxy-moving-maintenance");
                send(player, settings().lobbyServer());
            }
        }
        report(false);
        // Whoever is still here a moment later (lobby down?) is kicked like without a proxy.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.maintenance().active()) {
                return;
            }
            for (Player player : Npcs.online()) {
                if (!plugin.adminMode().active(player)) {
                    player.kick(messages().as(player, plugin.maintenance()::kickScreen));
                }
            }
        }, 100L);
    }

    /**
     * Main server, pre-login (not the server thread): takes a switch from the lobby. Called for every login, so an
     * admin also gets the admin mode when no maintenance runs.
     */
    public void preLogin(UUID player) {
        if (main() && plugin.storage().takeSwitch(player, settings().mainServer())) {
            switching.add(player);
        }
    }

    /** Main server during maintenance: who may join - team in admin mode (saved) or switching into it. */
    public boolean mayJoinDuringMaintenance(UUID player, boolean bypass) {
        return switching.contains(player) || (bypass && plugin.adminMode().saved(player));
    }

    /** Main server, during maintenance: may this player stay (in game, e.g. join check without LuckPerms). */
    public boolean mayStay(Player player) {
        return mayJoinDuringMaintenance(player.getUniqueId(), player.hasPermission(at.marcelr.mradmin.Perms.MAINTENANCE_BYPASS));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (main() && switching.remove(player.getUniqueId())) {
            // After the admin mode had its chance to pick up a saved state.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && !plugin.adminMode().active(player)) {
                    plugin.adminMode().enable(player);
                }
            });
        } else if (lobby()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    arrived(player);
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BossBar bar = bars.remove(event.getPlayer().getUniqueId());
        if (bar != null) {
            event.getPlayer().hideBossBar(bar);
        }
        returning.remove(event.getPlayer().getUniqueId());
    }

    /** /stop and /restart of the console go through {@link #shutdown}. */
    @EventHandler(ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        Boolean restart = stopCommand(event.getCommand());
        if (restart != null && main()) {
            event.setCancelled(true);
            shutdown(restart);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Boolean restart = stopCommand(event.getMessage().substring(1));
        if (restart != null && main() && event.getPlayer().hasPermission(restart ? "bukkit.command.restart" : "minecraft.command.stop")) {
            event.setCancelled(true);
            shutdown(restart);
        }
    }

    /** true = restart, false = stop, null = something else. */
    private static Boolean stopCommand(String line) {
        String command = line.trim().split(" ", 2)[0].toLowerCase(Locale.ROOT);
        command = command.substring(command.indexOf(':') + 1);
        return switch (command) {
            case "stop" -> false;
            case "restart" -> true;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ lobby

    /** Lobby, async: what does the main server say, and can we reach it? */
    private void watch() {
        Storage.ServerState report = null;
        for (Storage.ServerState s : plugin.storage().serverStates()) {
            YamlConfiguration y = yaml(s.data());
            if ("main".equals(y.getString("role")) && (report == null || s.ageMillis() < report.ageMillis())) {
                report = s;
            }
        }
        boolean reachable = reachable();
        Storage.ServerState found = report;
        Bukkit.getScheduler().runTask(plugin, () -> update(found, reachable));
    }

    private boolean reachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(settings().mainHost(), settings().mainPort()), 1500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (InvalidConfigurationException ignored) {
            // broken report - counts as none
        }
        return y;
    }

    private void update(Storage.ServerState report, boolean reachable) {
        if (!lobby()) {
            return;
        }
        MainState before = state;
        window = null;
        if (report == null) {
            state = MainState.OFFLINE;
        } else {
            YamlConfiguration y = yaml(report.data());
            boolean fresh = report.ageMillis() < FRESH_MILLIS;
            boolean stopped = "stopping".equals(y.getString("state"));
            if (y.isConfigurationSection("maintenance")) {
                long start = y.getLong("maintenance.start");
                long end = y.getLong("maintenance.end");
                boolean active = y.getBoolean("maintenance.active");
                window = new Window(Window.MANUAL, 0, y.getString("maintenance.title", ""),
                        start == 0 ? null : Instant.ofEpochMilli(start), end == 0 ? null : Instant.ofEpochMilli(end), active);
                if (!active || (end != 0 && end < System.currentTimeMillis() && !fresh)) {
                    window = null;
                }
            }
            if (window != null && window.running()) {
                state = MainState.MAINTENANCE;
            } else if (fresh && !stopped && reachable) {
                state = MainState.READY;
            } else if (stopped && report.ageMillis() < RESTART_MILLIS) {
                state = MainState.RESTARTING;
            } else {
                state = MainState.OFFLINE;
            }
        }
        if (state == MainState.READY && before != MainState.READY) {
            Npcs.online().stream().filter(p -> !p.hasPermission(PERM_STAY)).map(Player::getUniqueId)
                    .filter(id -> !returning.contains(id)).forEach(returning::add);
        } else if (state != MainState.READY) {
            returning.clear();
        }
        Npcs.online().forEach(this::showReason);
    }

    /** Lobby: a player arrives - to the spawn, and why they wait. */
    private void arrived(Player player) {
        if (settings().teleportToSpawn()) {
            player.teleportAsync(player.getWorld().getSpawnLocation().add(0.5, 0, 0.5));
        }
        if (state == MainState.READY && !player.hasPermission(PERM_STAY) && !returning.contains(player.getUniqueId())) {
            returning.add(player.getUniqueId());
        }
        showReason(player);
    }

    private void showReason(Player player) {
        String key = switch (state) {
            case READY -> "proxy-wait-ready";
            case MAINTENANCE -> window != null && window.end() != null ? "proxy-wait-maintenance" : "proxy-wait-maintenance-open";
            case RESTARTING -> "proxy-wait-restart";
            case OFFLINE -> "proxy-wait-offline";
        };
        if (player.hasPermission(PERM_STAY) && state == MainState.READY) {
            key = "proxy-wait-stay";
        }
        var tags = plugin.maintenance().resolvers(window, Instant.now());
        String finalKey = key;
        Component text = messages().as(player, () -> messages().get(finalKey, tags));
        switch (settings().display()) {
            case BOSSBAR -> {
                BossBar.Color color = state == MainState.READY ? BossBar.Color.GREEN
                        : state == MainState.MAINTENANCE ? BossBar.Color.PURPLE : BossBar.Color.YELLOW;
                BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> BossBar.bossBar(text, 1f, color, BossBar.Overlay.PROGRESS));
                bar.name(text);
                bar.color(color);
                player.showBossBar(bar);
            }
            case ACTIONBAR -> player.sendActionBar(text);
            case NONE -> {
            }
        }
    }

    /** Lobby, every second: a few players back to the main server (not in the middle of a duel or the like). */
    private void sendBack() {
        if (state != MainState.READY) {
            return;
        }
        int budget = settings().returnPerSecond();
        int tries = returning.size();
        while (budget > 0 && tries-- > 0 && !returning.isEmpty()) {
            UUID id = returning.poll();
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            if (plugin.modules().busy(player) != null) {
                returning.add(id);
                continue;
            }
            messages().send(player, "proxy-returning");
            send(player, settings().mainServer());
            budget--;
        }
    }

    /**
     * Lobby: /mradmin mode - to the main server straight into admin mode. Returns false if the main server is not
     * there (the player was told).
     */
    public boolean switchToMain(Player player) {
        if (state != MainState.READY && state != MainState.MAINTENANCE) {
            messages().send(player, "proxy-main-offline");
            return false;
        }
        messages().send(player, "proxy-switching");
        UUID id = player.getUniqueId();
        plugin.storage().putSwitch(id, settings().mainServer(), SWITCH_MILLIS, () -> Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                send(online, settings().mainServer());
            }
        }));
        return true;
    }

    // ---------------------------------------------------------------- command

    /** /mradmin proxy [send <player|all> <main|lobby>] */
    public void command(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("send")) {
            if (!sender.hasPermission(PERM_SEND)) {
                messages().send(sender, "no-permission");
                return;
            }
            if (!running) {
                messages().send(sender, "proxy-off");
                return;
            }
            if (args.length < 4 || !(args[3].equalsIgnoreCase("main") || args[3].equalsIgnoreCase("lobby"))) {
                messages().send(sender, "proxy-send-usage");
                return;
            }
            String server = args[3].equalsIgnoreCase("main") ? settings().mainServer() : settings().lobbyServer();
            List<Player> targets = args[2].equalsIgnoreCase("all") ? Npcs.online() : new ArrayList<>();
            if (!args[2].equalsIgnoreCase("all")) {
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    messages().send(sender, "player-not-found", tag("player", args[2]));
                    return;
                }
                targets.add(target);
            }
            targets.forEach(p -> send(p, server));
            messages().send(sender, "proxy-sent", tag("count", targets.size()), tag("server", server));
            return;
        }
        if (!sender.hasPermission(PERM_VIEW)) {
            messages().send(sender, "no-permission");
            return;
        }
        if (!settings().enabled()) {
            messages().send(sender, "proxy-off");
            return;
        }
        messages().send(sender, "proxy-status-header", tag("role", lobby() ? "lobby" : "main"),
                tag("main", settings().mainServer()), tag("lobby", settings().lobbyServer()));
        if (lobby()) {
            messages().send(sender, "proxy-status-main", Messages.lazy("state",
                    () -> messages().get("proxy-state-" + state.name().toLowerCase(Locale.ROOT))),
                    tag("waiting", Npcs.online().size()), tag("returning", returning.size()));
        }
        if (problems.isEmpty()) {
            messages().send(sender, "proxy-status-ok");
        }
        for (String problem : problems) {
            messages().send(sender, "proxy-problem-" + problem);
        }
    }

    /** Lobby: the state of the main server (for the web and the status). */
    public MainState mainState() {
        return state;
    }
}

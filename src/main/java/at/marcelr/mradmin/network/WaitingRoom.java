package at.marcelr.mradmin.network;

import at.marcelr.mradmin.ConfigUpdater;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.maintenance.Window;
import at.marcelr.mradmin.news.News;
import at.marcelr.mradmin.player.Npcs;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Waiting room behind a proxy (section {@code waiting-room} of config.yml). Optional and off by default.
 * <ul>
 *     <li>Before a restart or stop, and when a maintenance starts, players are sent to the waiting room server of the
 *     proxy (BungeeCord channel {@code Connect}, which Velocity understands too) instead of being kicked.</li>
 *     <li>A small HTTP endpoint of its own ({@code waiting-room.link}, not the web interface) tells the waiting room
 *     what is going on: restart countdown, maintenance, warnings. The waiting room (plugin MRwaitroom) asks every few
 *     seconds and keeps the last answer while this server is down. Protected by a shared key.</li>
 * </ul>
 * MRadmin knows nothing about MRwaitroom: the endpoint is plain JSON, any waiting room may read it.
 */
public final class WaitingRoom implements Listener {

    /** Version of the JSON answer; raise only for changes that old waiting rooms cannot read. */
    public static final int PROTOCOL = 1;
    private static final String CHANNEL = "BungeeCord";
    private static final Gson GSON = new Gson();

    /** Settings of the section waiting-room. */
    public record Config(boolean enabled, String server, boolean onRestart, boolean onMaintenance, String bind,
                         int port, String key) {

        static Config from(FileConfiguration c) {
            String server = c.getString("waiting-room.server", "waitroom").trim();
            return new Config(c.getBoolean("waiting-room.enabled", false), server.isEmpty() ? "waitroom" : server,
                    c.getBoolean("waiting-room.on-restart", true), c.getBoolean("waiting-room.on-maintenance", true),
                    c.getString("waiting-room.link.bind", "127.0.0.1").trim(),
                    Math.clamp(c.getInt("waiting-room.link.port", 8126), 1, 65535),
                    c.getString("waiting-room.link.key", "").trim());
        }
    }

    private final MRAdmin plugin;
    private final RemotePlugins remote;
    private volatile Config config = new Config(false, "waitroom", true, true, "", 8126, "");
    private HttpServer server;
    private String boundTo = "";
    private BukkitTask snapshotTask;
    private volatile String snapshot = "{}";
    /** Whether the server has finished starting (ServerLoadEvent) - the waiting room only sends players back then. */
    private volatile boolean ready;
    /** True from the moment the server goes down (players are being sent away). */
    private volatile boolean stopping;
    /** Failed key checks per address (simple brake against guessing). */
    private final Map<String, int[]> failures = new ConcurrentHashMap<>();

    public WaitingRoom(MRAdmin plugin) {
        this.plugin = plugin;
        this.remote = new RemotePlugins(plugin);
    }

    /** Plugins on the waiting room server managed from here (configuration, updates). */
    public RemotePlugins remote() {
        return remote;
    }

    public Config config() {
        return config;
    }

    /** Whether players are sent to the waiting room (instead of being kicked). */
    public boolean active() {
        return config.enabled();
    }

    /** On enable and after every reload. */
    public void start() {
        Config c = Config.from(plugin.getConfig());
        if (c.enabled() && c.key().length() < 16) {
            String key = HexFormat.of().formatHex(random(24));
            writeKey(key);
            c = new Config(c.enabled(), c.server(), c.onRestart(), c.onMaintenance(), c.bind(), c.port(), key);
            plugin.getLogger().info("Created waiting-room.link.key - copy it into the config.yml of the waiting room.");
        }
        config = c;
        var messenger = Bukkit.getMessenger();
        if (c.enabled() && !messenger.isOutgoingChannelRegistered(plugin, CHANNEL)) {
            messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        }
        if (snapshotTask == null) {
            snapshotTask = Bukkit.getScheduler().runTaskTimer(plugin, this::buildSnapshot, 1L, 20L);
        }
        String target = c.enabled() && !c.bind().isEmpty() ? c.bind() + ":" + c.port() : "";
        if (!target.equals(boundTo)) {
            stopLink();
            if (!target.isEmpty()) {
                startLink(c);
            }
        }
    }

    public void stop() {
        stopLink();
        if (snapshotTask != null) {
            snapshotTask.cancel();
            snapshotTask = null;
        }
    }

    @EventHandler
    public void onLoad(ServerLoadEvent event) {
        ready = true;
        buildSnapshot();
    }

    // ------------------------------------------------------------------ sending

    /**
     * Sends everyone (except NPCs) to the waiting room - before a restart or stop. Marks the server as going down, so
     * the waiting room does not send them straight back.
     *
     * @return how many players were sent, 0 if the waiting room is off for restarts
     */
    public int sendAllBeforeStop() {
        if (!active() || !config.onRestart()) {
            return 0;
        }
        stopping = true;
        buildSnapshot();
        List<Player> players = new ArrayList<>(Npcs.online());
        for (Player player : players) {
            send(player);
        }
        if (!players.isEmpty()) {
            plugin.getLogger().info("Sent " + players.size() + " players to the waiting room '" + config.server() + "'.");
        }
        return players.size();
    }

    /**
     * During maintenance: sends the player to the waiting room instead of kicking them. Right after joining the proxy
     * may not take the message yet (it registers its channel for the player a moment later), so it is sent again
     * after a second - and whoever is still here after 5 seconds is kicked as without a waiting room. Without the
     * waiting room ({@code waiting-room.on-maintenance} off) {@code kick} runs at once.
     *
     * @param kick kicks the player (the maintenance screen)
     */
    public void sendForMaintenance(Player player, Runnable kick) {
        if (!active() || !config.onMaintenance()) {
            kick.run();
            return;
        }
        send(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && stillOut(player)) {
                send(player);
            }
        }, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && stillOut(player)) {
                plugin.getLogger().info(player.getName() + " did not move to the waiting room - kicking as usual.");
                kick.run();
            }
        }, 100L);
    }

    /** Still kept out by the maintenance (it may have ended or the player got the bypass meanwhile). */
    private boolean stillOut(Player player) {
        return plugin.maintenance().active() && !plugin.maintenance().canJoin(player);
    }

    /** Asks the proxy to move the player to the waiting room server. */
    public void send(Player player) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(config.server());
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Could not send " + player.getName() + " to the waiting room: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------- status

    /** What the waiting room gets - built on the server thread once per second. */
    private void buildSnapshot() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("protocol", PROTOCOL);
        json.put("time", System.currentTimeMillis());
        json.put("name", plugin.branding() == null ? "" : plugin.branding().name());
        json.put("ready", ready && !stopping);
        json.put("stopping", stopping);
        json.put("online", Npcs.online().size());
        var restarts = plugin.restarts();
        if (restarts != null && restarts.running()) {
            json.put("restart", Map.of("at", restarts.endsAt(), "restart", restarts.restarting(),
                    "reason", restarts.reason() == null ? "" : restarts.reason()));
        } else if (stopping) {
            json.put("restart", Map.of("at", System.currentTimeMillis(), "restart", restarts == null || restarts.restarting(),
                    "reason", restarts == null || restarts.reason() == null ? "" : restarts.reason()));
        }
        var maintenance = plugin.maintenance();
        if (maintenance != null && maintenance.current() != null) {
            Window w = maintenance.current();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("active", maintenance.active());
            m.put("title", w.title() == null ? "" : w.title());
            m.put("start", w.start() == null ? 0 : w.start().toEpochMilli());
            m.put("end", w.end() == null ? 0 : w.end().toEpochMilli());
            m.put("bypass", at.marcelr.mradmin.Perms.MAINTENANCE_BYPASS);
            json.put("maintenance", m);
        }
        List<Map<String, Object>> alerts = new ArrayList<>();
        if (plugin.web() != null) {
            for (News.Post post : plugin.web().news().activeAlerts()) {
                Map<String, String> title = new LinkedHashMap<>();
                for (String lang : News.LANGS) {
                    title.put(lang, post.heading(lang));
                }
                alerts.add(Map.of("id", post.id(), "title", title));
                if (alerts.size() >= 3) {
                    break;
                }
            }
        }
        json.put("alerts", alerts);
        snapshot = GSON.toJson(json);
    }

    // --------------------------------------------------------------------- link

    private void startLink(Config c) {
        try {
            server = HttpServer.create(new InetSocketAddress(c.bind(), c.port()), 0);
            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "MRadmin waiting room link");
                t.setDaemon(true);
                return t;
            }));
            server.createContext("/waiting-room/", this::handle);
            server.start();
            boundTo = c.bind() + ":" + c.port();
            plugin.getLogger().info("Waiting room link listening on " + boundTo + ".");
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().severe("Could not open the waiting room link on " + c.bind() + ":" + c.port() + ": "
                    + e.getMessage());
            server = null;
            boundTo = "";
        }
    }

    private void stopLink() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        boundTo = "";
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
            int[] failed = failures.computeIfAbsent(ip, k -> new int[2]);
            int minute = (int) (System.currentTimeMillis() / 60_000L);
            if (failed[1] != minute) {
                failed[0] = 0;
                failed[1] = minute;
            }
            if (failed[0] >= 10) {
                reply(exchange, 429, "{\"error\":\"rate-limit\"}");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            boolean report = path.equals("/waiting-room/report");
            if (!(report ? "POST" : "GET").equals(exchange.getRequestMethod())
                    || !(report || path.equals("/waiting-room/status"))) {
                reply(exchange, 405, "{\"error\":\"method\"}");
                return;
            }
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            String given = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : "";
            if (!MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), config.key().getBytes(StandardCharsets.UTF_8))
                    || config.key().isEmpty()) {
                failed[0]++;
                reply(exchange, 401, "{\"error\":\"key\"}");
                return;
            }
            if (report) {
                byte[] body = exchange.getRequestBody().readNBytes(600 * 1024 + 1);
                String problem = body.length > 600 * 1024 ? "too-large"
                        : remote.receive(new String(body, StandardCharsets.UTF_8));
                reply(exchange, problem == null ? 200 : 400, problem == null ? "{\"ok\":true}"
                        : "{\"error\":\"" + problem + "\"}");
                return;
            }
            // The state plus the open orders for remote plugins (configuration, updates).
            String state = snapshot;
            String orders = "\"orders\":" + remote.ordersJson() + "}";
            reply(exchange, 200, state.length() <= 2 ? "{" + orders : state.substring(0, state.length() - 1) + "," + orders);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Waiting room link request failed", e);
        }
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static byte[] random(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    /** Writes the new key into config.yml without touching the rest (comments stay). */
    private void writeKey(String key) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().parseComments(true);
            yaml.loadFromString(ConfigUpdater.stripBom(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
            yaml.set("waiting-room.link.key", key);
            yaml.options().width(10_000);
            Files.writeString(file.toPath(), ConfigUpdater.BOM + yaml.saveToString(), StandardCharsets.UTF_8);
            plugin.getConfig().set("waiting-room.link.key", key);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Could not write waiting-room.link.key to config.yml: " + e.getMessage());
        }
    }
}

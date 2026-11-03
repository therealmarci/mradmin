package at.marcelr.mradmin.map;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.api.AdminModule;
import at.marcelr.mradmin.api.MapLayer;
import at.marcelr.mradmin.api.MapMarker;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import static at.marcelr.mradmin.Messages.tag;

/**
 * The web map: MRadmin connects squaremap (if installed) and draws the layers the modules offer
 * ({@link AdminModule#mapLayers()}), e.g. MRchunks' chunk loaders. Also keeps squaremap's config fitting
 * ({@link SquaremapSetup}), hides players and adds the chunk grid. Until MRadmin 1.1 this was part of MRchunks.
 * <p>
 * This class must not reference any squaremap type itself: {@link SquaremapBridge} is only loaded when squaremap is
 * installed, so MRadmin runs fine without it.
 */
public final class MapService implements Listener {

    private final MRAdmin plugin;
    private final SquaremapSetup setup;
    private MapSettings settings;
    private SquaremapBridge squaremap;
    private BukkitTask periodic;
    private boolean refreshQueued;
    /** module id -> its icons under their squaremap key (mradmin_&lt;module&gt;_&lt;icon&gt;), asked once per start */
    private final Map<String, Map<String, BufferedImage>> icons = new HashMap<>();
    /** What the last update drew: "module:layer" -> layer name, for /mradmin map. */
    private Map<String, String> lastLayers = Map.of();
    /** Public IP of the server, detected once (see map.detect-public-ip); "" until known. */
    private volatile String publicIp = "";
    private boolean publicIpRequested;
    /** Language key of the squaremap status (map-state-*). */
    private String status = "map-state-missing";
    private String error = "";

    public MapService(MRAdmin plugin) {
        this.plugin = plugin;
        this.setup = new SquaremapSetup(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public boolean squaremapInstalled() {
        return Bukkit.getPluginManager().isPluginEnabled("squaremap");
    }

    /** (Re)starts the map - after all plugins are enabled, so their modules are registered. */
    public void start() {
        stop();
        settings = MapSettings.from(plugin.getConfig());
        icons.clear();
        if (!squaremapInstalled()) {
            status = "map-state-missing";
            return;
        }
        if (!settings.enabled()) {
            status = "map-state-disabled";
            return;
        }
        try {
            squaremap = new SquaremapBridge(plugin);
            refreshNow();
            status = "map-state-active";
            plugin.getLogger().info("squaremap found - the web map shows the layers of the plugins.");
        } catch (LinkageError | RuntimeException e) {
            squaremap = null;
            status = "map-state-error";
            error = e.getClass().getSimpleName();
            plugin.getLogger().log(Level.WARNING, "squaremap integration failed (is a matching squaremap version installed?)", e);
            return;
        }
        // Every start: is squaremap's config still as MRadmin recommends it (values changed by hand stay)?
        if (settings.autoConfigure()) {
            runSetup(Bukkit.getConsoleSender(), false);
        }
        detectPublicIp();
        long interval = settings.updateSeconds() * 20L;
        periodic = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshNow, interval, interval);
    }

    public void stop() {
        if (periodic != null) {
            periodic.cancel();
            periodic = null;
        }
        if (squaremap != null) {
            try {
                squaremap.hidePlayers(false);
                squaremap.remove();
            } catch (LinkageError | RuntimeException e) {
                // squaremap may already be disabled during shutdown
            }
            squaremap = null;
        }
        lastLayers = Map.of();
    }

    /**
     * Brings the recommended squaremap settings up to date and reloads squaremap if something changed.
     * @param force also overwrite values changed by hand (/mradmin map setup)
     */
    public void runSetup(CommandSender sender, boolean force) {
        try {
            List<SquaremapSetup.Change> changes = setup.apply(settings(), force);
            if (changes.isEmpty()) {
                if (force) {
                    plugin.messages().send(sender, "map-setup-nothing");
                }
                return;
            }
            Messages messages = plugin.messages();
            plugin.getLogger().info("squaremap configured: " + String.join(", ", changes.stream()
                    .map(c -> messages.console(c.key(), c.placeholders())).toList()) + ".");
            if (sender != Bukkit.getConsoleSender()) {
                messages.send(sender, "map-setup-done", tag("changes", String.join(", ", changes.stream()
                        .map(c -> messages.plain(c.key(), c.placeholders())).toList())));
            }
            // squaremap reads its config only on (re)load; the layers and the grid are restored right after.
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "squaremap reload");
                Bukkit.getScheduler().runTaskLater(plugin, this::refreshNow, 40L);
            });
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not adjust the squaremap config", e);
            plugin.messages().send(sender, "map-setup-failed", tag("reason", String.valueOf(e.getMessage())));
        }
    }

    private MapSettings settings() {
        if (settings == null) {
            settings = MapSettings.from(plugin.getConfig());
        }
        return settings;
    }

    // ------------------------------------------------------------ drawing

    /** Something changed (module API); several changes in one tick lead to a single update. */
    public void refreshSoon() {
        if (squaremap == null || refreshQueued || !plugin.isEnabled()) {
            return;
        }
        refreshQueued = true;
        Bukkit.getScheduler().runTask(plugin, this::refreshNow);
    }

    private void refreshNow() {
        refreshQueued = false;
        if (squaremap == null) {
            return;
        }
        List<SquaremapBridge.Drawn> drawn = new ArrayList<>();
        Map<String, BufferedImage> pictures = new HashMap<>();
        Map<String, String> listed = new LinkedHashMap<>();
        for (AdminModule module : plugin.modules().all()) {
            String moduleId = safe(module.id());
            List<MapLayer> layers = plugin.modules().call(module, module::mapLayers, List.of());
            if (layers.isEmpty()) {
                continue;
            }
            Map<String, BufferedImage> cached = icons.get(moduleId);
            if (cached == null || missingIcon(moduleId, layers, cached)) {
                // First time, or a marker names a picture not seen yet (e.g. a new category) - ask the module again.
                Map<String, BufferedImage> named = new HashMap<>();
                plugin.modules().call(module, module::mapIcons, Map.<String, BufferedImage>of())
                        .forEach((key, image) -> named.put(iconKey(moduleId, key), image));
                icons.put(moduleId, named);
                cached = named;
            }
            Map<String, BufferedImage> own = cached;
            pictures.putAll(own);
            for (MapLayer layer : layers) {
                String id = moduleId + ":" + safe(layer.id());
                listed.put(id, layer.name());
                if (settings.offLayers().contains(id)) {
                    continue;
                }
                MapLayer withIcons = new MapLayer(layer.id(), layer.name(), layer.defaultHidden(), layer.priority(),
                        layer.markers().stream().map(m -> withIconKey(moduleId, m, own)).filter(m -> m != null).toList());
                drawn.add(new SquaremapBridge.Drawn("mradmin_" + moduleId + "_" + safe(layer.id()), withIcons,
                        layer.defaultHidden() || settings.hiddenLayers().contains(id)));
            }
        }
        lastLayers = listed;
        try {
            squaremap.installGrid(settings);
            squaremap.draw(drawn, pictures);
            squaremap.hidePlayers(settings.hidePlayers());
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "squaremap: could not update the markers", e);
        }
    }

    private static boolean missingIcon(String moduleId, List<MapLayer> layers, Map<String, BufferedImage> icons) {
        for (MapLayer layer : layers) {
            for (MapMarker marker : layer.markers()) {
                if (marker instanceof MapMarker.Icon icon && !icons.containsKey(iconKey(moduleId, icon.icon()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Icon markers name the module's own key; squaremap knows the icon under mradmin_&lt;module&gt;_&lt;key&gt;. */
    private static @Nullable MapMarker withIconKey(String moduleId, MapMarker marker, Map<String, BufferedImage> icons) {
        if (marker instanceof MapMarker.Icon icon) {
            String key = iconKey(moduleId, icon.icon());
            if (!icons.containsKey(key)) {
                return null; // the module offers no such picture
            }
            return new MapMarker.Icon(icon.id(), icon.world(), icon.x(), icon.z(), key, icon.size(), icon.hover(), icon.click());
        }
        return marker;
    }

    private static String iconKey(String moduleId, String key) {
        return "mradmin_" + moduleId + "_" + safe(key);
    }

    /** squaremap keys allow letters, digits, "_", "-" and "." only. */
    static String safe(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (squaremap != null && settings.hidePlayers()) {
            try {
                squaremap.hide(event.getPlayer().getUniqueId());
            } catch (LinkageError | RuntimeException e) {
                // ignore - squaremap not ready
            }
        }
    }

    // ------------------------------------------------------------ links

    /** Public link to the web map, or empty if there is none. */
    public String url() {
        if (squaremap != null) {
            try {
                return squaremap.publicUrl(settings, publicIp);
            } catch (LinkageError | RuntimeException e) {
                return "";
            }
        }
        return "";
    }

    /** Link centred on a place (?world=...&amp;zoom=...&amp;x=...&amp;z=...), or empty if the world is not on the map. */
    public String url(World world, int x, int z) {
        String base = url();
        try {
            if (base.isEmpty() || !squaremap.onMap(world)) {
                return "";
            }
        } catch (LinkageError | RuntimeException e) {
            return "";
        }
        String prefix = base.contains("?") ? base + "&" : base + "?";
        return prefix + "world=" + world.getKey().asString().replace(':', '_') + "&zoom=4&x=" + x + "&z=" + z;
    }

    /**
     * Without map.address and without a usable server-ip the link needs the public IP. It is asked for once, in the
     * background, at checkip.amazonaws.com (only the request, no data is sent). Off with detect-public-ip: false.
     */
    private void detectPublicIp() {
        if (publicIpRequested || !settings.detectPublicIp() || !settings.address().isEmpty() || MapAddress.usable(Bukkit.getIp())) {
            return;
        }
        publicIpRequested = true;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://checkip.amazonaws.com"))
                .timeout(Duration.ofSeconds(10)).GET().build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, failure) -> {
            String body = failure == null && response.statusCode() == 200 ? response.body().trim() : "";
            if (body.matches("[0-9a-fA-F.:]{3,45}")) {
                publicIp = body;
                plugin.getLogger().info("Public IP for the web map link: " + body + " (set map.address to use a domain).");
            } else {
                publicIpRequested = false; // try again on the next reload
                plugin.getLogger().info("Could not detect the public IP for the web map link - set map.address.");
            }
        });
    }

    // ------------------------------------------------- map of the player page

    /** squaremap's tile folder, or null without an active squaremap. */
    public java.nio.file.@Nullable Path tilesDir() {
        try {
            return squaremap == null ? null : squaremap.tilesDir();
        } catch (LinkageError | RuntimeException e) {
            return null;
        }
    }

    /**
     * The worlds squaremap renders, for MRadmin's own map: key ("minecraft:overworld"), tile folder name, display
     * name, zoom levels (default, max = 1 block per pixel, extra = enlarged beyond that) and spawn. Read from
     * squaremap's settings.json files; empty without squaremap.
     */
    public List<Map<String, Object>> worlds() {
        java.nio.file.Path tiles = tilesDir();
        List<Map<String, Object>> result = new ArrayList<>();
        if (tiles == null) {
            return result;
        }
        try {
            var all = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(tiles.resolve("settings.json")))
                    .getAsJsonObject().getAsJsonArray("worlds");
            for (var element : all) {
                var w = element.getAsJsonObject();
                String folder = w.get("name").getAsString();
                java.nio.file.Path own = tiles.resolve(folder).resolve("settings.json");
                if (!folder.matches("[a-z0-9_.-]+") || !java.nio.file.Files.isRegularFile(own)) {
                    continue;
                }
                var s = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(own)).getAsJsonObject();
                var zoom = s.getAsJsonObject("zoom");
                var spawn = s.getAsJsonObject("spawn");
                Map<String, Object> map = new LinkedHashMap<>();
                // squaremap names the folder after the world key with "_" instead of ":".
                World world = Bukkit.getWorlds().stream()
                        .filter(b -> b.getKey().asString().replace(':', '_').equals(folder)).findFirst().orElse(null);
                map.put("key", world != null ? world.getKey().asString() : folder.replaceFirst("_", ":"));
                map.put("folder", folder);
                map.put("name", w.has("display_name") ? w.get("display_name").getAsString() : folder);
                map.put("type", w.has("type") ? w.get("type").getAsString() : "normal");
                map.put("zoomDefault", zoom.get("def").getAsInt());
                map.put("zoomMax", zoom.get("max").getAsInt());
                map.put("zoomExtra", zoom.get("extra").getAsInt());
                map.put("spawnX", spawn.get("x").getAsInt());
                map.put("spawnZ", spawn.get("z").getAsInt());
                result.add(map);
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().fine("squaremap settings not readable yet: " + e);
        }
        return result;
    }

    // ------------------------------------------------------------ status

    public boolean active() {
        return squaremap != null;
    }

    /** "active", "not installed", "disabled in the config" or an error - in the recipient's language. */
    public String status() {
        return plugin.messages().plain(status, "error", error);
    }

    /** The layers of the last update: "module:layer" -> name (also the switched-off ones). */
    public Map<String, String> layers() {
        return lastLayers;
    }

    /** "on", "hidden" or "off" - how the layer is configured in map.layers. */
    public String layerState(String id) {
        return settings().offLayers().contains(id) ? "off" : settings().hiddenLayers().contains(id) ? "hidden" : "on";
    }
}

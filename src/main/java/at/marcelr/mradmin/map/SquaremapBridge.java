package at.marcelr.mradmin.map;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.api.MapLayer;
import at.marcelr.mradmin.api.MapMarker;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import xyz.jpenilla.squaremap.api.BukkitAdapter;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything that touches squaremap's API: the modules' layers and icons, the hidden players and the chunk grid.
 * Only loaded by {@link MapService} when squaremap is installed. Layers are filled from scratch on every update -
 * with a few hundred markers that is cheap and avoids any drift.
 */
final class SquaremapBridge {

    /** A module's layer as squaremap gets it: the key (mradmin_&lt;module&gt;_&lt;layer&gt;) and what it shows. */
    record Drawn(String key, MapLayer layer, boolean defaultHidden) {
    }

    private static final String GRID_FILE = "mradmin-grid.js";
    private static final String GRID_MARKER = "<!-- mradmin-grid -->";
    /** What MRchunks injected until MRadmin took the map over - removed again. */
    private static final String LEGACY_GRID_FILE = "mrchunks-grid.js";
    private static final String LEGACY_GRID_MARKER = "<!-- mrchunks-grid -->";

    private final MRAdmin plugin;
    private final Squaremap api;
    /** world -> layers registered there, with the look they were registered with (re-registered on a change) */
    private final Map<UUID, Map<Key, Drawn>> layers = new HashMap<>();
    private final Set<Key> icons = new HashSet<>();

    SquaremapBridge(MRAdmin plugin) {
        this.plugin = plugin;
        this.api = SquaremapProvider.get();
    }

    // ------------------------------------------------------------ layers

    /**
     * Draws the layers in every world on the map. Icons missing in squaremap (new, or lost on /squaremap reload) are
     * registered from {@code pictures}; layers of an earlier update that are gone now are removed.
     */
    void draw(List<Drawn> drawn, Map<String, BufferedImage> pictures) {
        pictures.forEach((name, image) -> {
            Key key = Key.of(name);
            if (!api.iconRegistry().hasEntry(key)) {
                api.iconRegistry().register(key, image);
            }
            icons.add(key);
        });
        for (World world : Bukkit.getWorlds()) {
            MapWorld mapWorld = api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(world)).orElse(null);
            if (mapWorld == null) {
                continue;
            }
            Map<Key, Drawn> own = layers.computeIfAbsent(world.getUID(), k -> new HashMap<>());
            Set<Key> wanted = new HashSet<>();
            for (Drawn d : drawn) {
                Key key = Key.of(d.key());
                wanted.add(key);
                SimpleLayerProvider layer = layer(mapWorld, own, key, d);
                layer.clearMarkers();
                for (MapMarker marker : d.layer().markers()) {
                    if (marker.world().equals(world.getUID())) {
                        add(layer, marker);
                    }
                }
            }
            for (Key old : Set.copyOf(own.keySet())) {
                if (!wanted.contains(old)) {
                    own.remove(old);
                    if (mapWorld.layerRegistry().hasEntry(old)) {
                        mapWorld.layerRegistry().unregister(old);
                    }
                }
            }
        }
    }

    /** The layer in this world, registered again if squaremap lost it (/squaremap reload) or its look changed. */
    private SimpleLayerProvider layer(MapWorld mapWorld, Map<Key, Drawn> own, Key key, Drawn d) {
        Drawn before = own.get(key);
        boolean same = before != null && before.layer().name().equals(d.layer().name())
                && before.defaultHidden() == d.defaultHidden() && before.layer().priority() == d.layer().priority();
        if (same && mapWorld.layerRegistry().hasEntry(key)
                && mapWorld.layerRegistry().get(key) instanceof SimpleLayerProvider existing) {
            own.put(key, d);
            return existing;
        }
        SimpleLayerProvider layer = SimpleLayerProvider.builder(d.layer().name())
                .showControls(true)
                .defaultHidden(d.defaultHidden())
                .layerPriority(5 + d.layer().priority())
                .zIndex(250 - d.layer().priority())
                .build();
        if (mapWorld.layerRegistry().hasEntry(key)) {
            mapWorld.layerRegistry().unregister(key);
        }
        mapWorld.layerRegistry().register(key, layer);
        own.put(key, d);
        return layer;
    }

    private void add(SimpleLayerProvider layer, MapMarker marker) {
        Key key = Key.of(MapService.safe(marker.id()));
        switch (marker) {
            case MapMarker.Area area -> {
                MapMarker.Style s = area.style();
                MarkerOptions.Builder options = MarkerOptions.builder()
                        .strokeColor(new Color(s.strokeColor()))
                        .strokeWeight(s.strokeWeight())
                        .strokeOpacity(s.strokeOpacity())
                        .fillColor(new Color(s.fillColor()))
                        .fillOpacity(s.fillOpacity());
                tooltips(options, area.hover(), area.click());
                layer.addMarker(key, Marker.rectangle(Point.of(area.minX(), area.minZ()), Point.of(area.maxX(), area.maxZ()))
                        .markerOptions(options.build()));
            }
            case MapMarker.Icon icon -> {
                MarkerOptions.Builder options = MarkerOptions.builder();
                tooltips(options, icon.hover(), icon.click());
                layer.addMarker(key, Marker.icon(Point.of(icon.x(), icon.z()), Key.of(icon.icon()), icon.size())
                        .markerOptions(options.build()));
            }
        }
    }

    private static void tooltips(MarkerOptions.Builder options, String hover, String click) {
        if (hover != null && !hover.isEmpty()) {
            options.hoverTooltip(hover);
        }
        if (click != null && !click.isEmpty()) {
            options.clickTooltip(click);
        }
    }

    /** Removes all layers and icons of MRadmin (on disable / when the map is switched off). */
    void remove() {
        for (World world : Bukkit.getWorlds()) {
            Map<Key, Drawn> own = layers.get(world.getUID());
            if (own == null) {
                continue;
            }
            api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(world)).ifPresent(mapWorld -> {
                for (Key key : own.keySet()) {
                    if (mapWorld.layerRegistry().hasEntry(key)) {
                        mapWorld.layerRegistry().unregister(key);
                    }
                }
            });
        }
        layers.clear();
        for (Key key : icons) {
            if (api.iconRegistry().hasEntry(key)) {
                api.iconRegistry().unregister(key);
            }
        }
        icons.clear();
        try {
            removeGrid();
        } catch (IOException e) {
            plugin.getLogger().warning("squaremap: could not remove the chunk grid: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------- players

    /** Hides (or shows again) all online players on the map; not persistent, applied again on every join. */
    void hidePlayers(boolean hide) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            api.playerManager().hidden(player.getUniqueId(), hide, false);
        }
    }

    void hide(UUID player) {
        api.playerManager().hide(player, false);
    }

    // ------------------------------------------------------------ address

    /** Address of the map for players, see {@link MapAddress}. */
    String publicUrl(MapSettings settings, String publicIp) {
        String webAddress = "";
        int port = 8080;
        Path config = api.webDir().getParent().resolve("config.yml");
        if (Files.exists(config)) {
            YamlConfiguration squaremapConfig = YamlConfiguration.loadConfiguration(config.toFile());
            webAddress = squaremapConfig.getString("settings.web-address", "");
            port = squaremapConfig.getInt("settings.internal-webserver.port", 8080);
        }
        return MapAddress.build(settings.address(), settings.reverseProxy(), webAddress, Bukkit.getIp(), publicIp, port);
    }

    /** Whether squaremap shows the world. */
    boolean onMap(World world) {
        return api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(world)).isPresent();
    }

    // -------------------------------------------------------- chunk grid

    /**
     * Copies the grid script into squaremap's web folder and includes it in index.html, before squaremap's own
     * module script (the grid script must hook into Leaflet before squaremap creates the map). Idempotent; called
     * on every update because squaremap rewrites its web files on (re)load.
     */
    void installGrid(MapSettings settings) {
        MapSettings.Grid grid = settings.grid();
        Path web = api.webDir();
        Path index = web.resolve("index.html");
        try {
            Files.deleteIfExists(web.resolve(LEGACY_GRID_FILE));
            if (!grid.enabled() && !settings.hidePlayers()) {
                removeGrid(); // nothing for the script to do
                return;
            }
            String script = gridScript(settings);
            Path target = web.resolve(GRID_FILE);
            if (!Files.exists(target) || !Files.readString(target, StandardCharsets.UTF_8).equals(script)) {
                Files.writeString(target, script, StandardCharsets.UTF_8);
            }
            if (!Files.exists(index)) {
                return;
            }
            String html = Files.readString(index, StandardCharsets.UTF_8);
            // Version parameter: browsers fetch the script again whenever the grid settings change.
            String tag = GRID_MARKER + "<script src=\"" + GRID_FILE + "?v=" + Integer.toHexString(script.hashCode()) + "\"></script>";
            String cleaned = withoutGrid(html);
            int module = cleaned.indexOf("<script type=\"module\"");
            if (module < 0) {
                return; // unknown squaremap layout - leave it alone
            }
            String patched = cleaned.substring(0, module) + tag + "\n      " + cleaned.substring(module);
            if (!patched.equals(html)) {
                Files.writeString(index, patched, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("squaremap: could not inject the chunk grid: " + e.getMessage());
        }
    }

    private static String withoutGrid(String html) {
        return html.replaceAll("(?m)^.*(" + GRID_MARKER + "|" + LEGACY_GRID_MARKER + ").*\\R?", "");
    }

    private void removeGrid() throws IOException {
        Path web = api.webDir();
        Path index = web.resolve("index.html");
        if (Files.exists(index)) {
            String html = Files.readString(index, StandardCharsets.UTF_8);
            String cleaned = withoutGrid(html);
            if (!cleaned.equals(html)) {
                Files.writeString(index, cleaned, StandardCharsets.UTF_8);
            }
        }
        Files.deleteIfExists(web.resolve(GRID_FILE));
        Files.deleteIfExists(web.resolve(LEGACY_GRID_FILE));
    }

    private String gridScript(MapSettings settings) throws IOException {
        MapSettings.Grid grid = settings.grid();
        JsonObject config = new JsonObject();
        config.addProperty("gridEnabled", grid.enabled());
        config.addProperty("defaultVisible", grid.defaultVisible());
        config.addProperty("color", grid.color());
        config.addProperty("opacity", grid.opacity());
        config.addProperty("minChunkPixels", grid.minChunkPixels());
        config.addProperty("regionLines", grid.regionLines());
        config.addProperty("regionColor", grid.regionColor());
        config.addProperty("labels", grid.labels());
        config.addProperty("label", plugin.messages().server(() -> plugin.messages().plain("map-grid-label")));
        config.addProperty("hidePlayerList", settings.hidePlayers());
        try (InputStream in = plugin.getResource("map/squaremap-grid.js")) {
            if (in == null) {
                throw new IOException("map/squaremap-grid.js is missing in the plugin");
            }
            // Gson output is valid JavaScript and escapes "<" etc., so config values cannot break out.
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("__MRADMIN_CONFIG__", config.toString());
        }
    }
}

package at.marcelr.mradmin.map;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static at.marcelr.mradmin.Messages.tag;

/**
 * The map: squaremap only renders the pictures, everything else is MRadmin's - the map of the player page (tab
 * "Map", {@link at.marcelr.mradmin.web.self} SelfMap) shows them with the plugins' layers. MRadmin installs and updates
 * squaremap itself ({@link SquaremapInstaller}), keeps its config fitting with its own web server off
 * ({@link SquaremapSetup}) and starts a full render of worlds that have no pictures yet.
 * <p>
 * This class must not reference any squaremap type itself: {@link SquaremapBridge} is only loaded when squaremap is
 * installed, so MRadmin runs fine without it.
 */
public final class MapService {

    /** Ending of the map key of a Nether ceiling: minecraft:the_nether#roof. */
    public static final String ROOF_KEY = "#roof";

    private final MRAdmin plugin;
    private final SquaremapSetup setup;
    private final SquaremapInstaller installer;
    private MapSettings settings;
    private SquaremapBridge squaremap;
    private BukkitTask installCheck;
    private @Nullable RoofRenderer roof;
    /** Language key of the squaremap status (map-state-*). */
    private String status = "map-state-missing";
    private String error = "";

    public MapService(MRAdmin plugin) {
        this.plugin = plugin;
        this.setup = new SquaremapSetup(plugin);
        this.installer = new SquaremapInstaller(plugin);
    }

    public boolean squaremapInstalled() {
        return Bukkit.getPluginManager().isPluginEnabled("squaremap");
    }

    /** (Re)starts the map - after all plugins are enabled, so their modules (and the worlds they own) are there. */
    public void start() {
        stop();
        settings = MapSettings.from(plugin.getConfig());
        if (!settings.enabled()) {
            status = "map-state-disabled";
            return;
        }
        // Install or update squaremap in the background, on start and then regularly.
        long period = settings.checkHours() * 3600L * 20L;
        installCheck = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                () -> installer.check(settings.autoInstall(), settings.autoUpdate()), 20L * 30, period);
        if (!squaremapInstalled()) {
            status = installer.staged() != null ? "map-state-installed" : "map-state-missing";
            return;
        }
        try {
            squaremap = new SquaremapBridge();
            status = "map-state-active";
            plugin.getLogger().info("squaremap found - it renders the map of the player page.");
        } catch (LinkageError | RuntimeException e) {
            squaremap = null;
            status = "map-state-error";
            error = e.getClass().getSimpleName();
            plugin.getLogger().log(Level.WARNING, "squaremap integration failed (is a matching squaremap version installed?)", e);
            return;
        }
        // Every start: is squaremap's config still as MRadmin needs it (values changed by hand stay)?
        if (settings.autoConfigure()) {
            runSetup(Bukkit.getConsoleSender(), false);
        }
        // Worlds without any picture yet (new install, new world): render them once completely.
        Bukkit.getScheduler().runTaskLater(plugin, this::renderMissing, 20L * 20);
        // The Nether ceiling: drawn by MRadmin itself (squaremap shows the Nether from the inside).
        if (settings.roof().enabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (squaremap != null && roof == null) {
                    roof = new RoofRenderer(plugin, settings.roof());
                    roof.start(worlds());
                }
            }, 20L * 25);
        }
    }

    public void stop() {
        if (installCheck != null) {
            installCheck.cancel();
            installCheck = null;
        }
        if (roof != null) {
            roof.stop();
            roof = null;
        }
        squaremap = null;
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
            // squaremap reads its config only on (re)load.
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "squaremap reload"));
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not adjust the squaremap config", e);
            plugin.messages().send(sender, "map-setup-failed", tag("reason", String.valueOf(e.getMessage())));
        }
    }

    /**
     * A full render (all generated chunks, squaremap renders in the background) of every world on the map - once after
     * squaremap was installed or updated (map.squaremap.full-render; the version rendered last is kept in
     * map-render.yml), otherwise only of worlds without pictures yet.
     */
    private void renderMissing() {
        Path tiles = tilesDir();
        if (tiles == null) {
            return;
        }
        java.io.File memory = new java.io.File(plugin.getDataFolder(), "map-render.yml");
        var rendered = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(memory);
        String version = String.valueOf(version());
        boolean all = settings().fullRender() && !version.equals(rendered.getString("rendered-version"));
        if (all) {
            plugin.getLogger().info("squaremap " + version + " is new (installed or updated) - full render of all worlds.");
            rendered.set("rendered-version", version);
            try {
                rendered.save(memory);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save map-render.yml: " + e.getMessage());
            }
        }
        for (World world : Bukkit.getWorlds()) {
            try {
                if (!squaremap.onMap(world)) {
                    continue;
                }
            } catch (LinkageError | RuntimeException e) {
                return;
            }
            Path folder = tiles.resolve(world.getKey().asString().replace(':', '_'));
            if (all) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "squaremap fullrender " + world.getKey().asString());
            } else if (!hasPictures(folder)) {
                plugin.getLogger().info("No map pictures of " + world.getName() + " yet - starting a full render (squaremap).");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "squaremap fullrender " + world.getKey().asString());
            }
        }
    }

    private static boolean hasPictures(Path folder) {
        try (var zooms = Files.list(folder)) {
            return zooms.anyMatch(Files::isDirectory);
        } catch (IOException e) {
            return false;
        }
    }

    public MapSettings settings() {
        if (settings == null) {
            settings = MapSettings.from(plugin.getConfig());
        }
        return settings;
    }

    // ------------------------------------------------------------ links

    /** Link to the map of the player page (web.public-url + /me/map), or "" without a public address or player page. */
    public String url() {
        String base = plugin.settings().web().publicUrl();
        if (squaremap == null || base == null || base.isBlank() || !plugin.settings().playerWeb().enabled()) {
            return "";
        }
        return base.replaceAll("/+$", "") + "/me/map";
    }

    /** Link to the map centred on a place, or "" if there is none or the world is not on the map. */
    public String url(World world, int x, int z) {
        String base = url();
        try {
            if (base.isEmpty() || !squaremap.onMap(world)) {
                return "";
            }
        } catch (LinkageError | RuntimeException e) {
            return "";
        }
        return base + "?world=" + world.getKey().asString() + "&x=" + x + "&z=" + z;
    }

    // ------------------------------------------------- map of the player page

    /** Folder of the Nether ceiling pictures (MRadmin's own), or null without them. */
    public @Nullable Path roofDir() {
        return roof == null ? null : roof.dir();
    }

    /**
     * The map a position belongs to: the world's key, for a player on the Nether ceiling the key of the ceiling map
     * (so the map of the player page switches to it by itself).
     */
    public String mapKey(World world, double y) {
        String key = world.getKey().asString();
        return roof != null && roof.covers(world) && y >= roof.fromY() ? key + ROOF_KEY : key;
    }
    /** squaremap's tile folder, or null without an active squaremap. */
    public @Nullable Path tilesDir() {
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
        Path tiles = tilesDir();
        List<Map<String, Object>> result = new ArrayList<>();
        if (tiles == null) {
            return result;
        }
        try {
            var all = com.google.gson.JsonParser.parseString(Files.readString(tiles.resolve("settings.json")))
                    .getAsJsonObject().getAsJsonArray("worlds");
            for (var element : all) {
                var w = element.getAsJsonObject();
                String folder = w.get("name").getAsString();
                Path own = tiles.resolve(folder).resolve("settings.json");
                if (!folder.matches("[a-z0-9_.-]+") || !Files.isRegularFile(own)) {
                    continue;
                }
                var s = com.google.gson.JsonParser.parseString(Files.readString(own)).getAsJsonObject();
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
                // The Nether ceiling as a world of its own right after it: same dimension (base), own pictures.
                if (roof != null && roof.coversFolder(folder)) {
                    Map<String, Object> ceiling = new LinkedHashMap<>(map);
                    ceiling.put("key", map.get("key") + ROOF_KEY);
                    ceiling.put("folder", folder + RoofRenderer.SUFFIX);
                    ceiling.put("base", map.get("key"));
                    ceiling.put("roof", true);
                    ceiling.put("roofY", roof.fromY());
                    result.add(ceiling);
                }
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

    /** "active", "not installed", "installed, active after a restart", ... - in the recipient's language. */
    public String status() {
        return plugin.messages().plain(status, "error", error);
    }

    /** squaremap version waiting for the next start (installed or updated by MRadmin), or null. */
    public @Nullable String staged() {
        return installer.staged();
    }

    /** The last problem of the automatic installation, or "". */
    public String installError() {
        return installer.lastError();
    }

    /** Version of the running squaremap, or null. */
    public @Nullable String version() {
        var squaremapPlugin = Bukkit.getPluginManager().getPlugin("squaremap");
        return squaremapPlugin == null ? null : squaremapPlugin.getPluginMeta().getVersion();
    }
}

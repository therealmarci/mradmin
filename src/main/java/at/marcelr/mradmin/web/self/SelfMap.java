package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.api.AdminModule;
import at.marcelr.mradmin.api.SelfMapLayer;
import at.marcelr.mradmin.api.SelfViewer;
import at.marcelr.mradmin.api.WebModule;
import at.marcelr.mradmin.map.MapService;
import at.marcelr.mradmin.map.MapSettings;
import at.marcelr.mradmin.player.Npcs;
import at.marcelr.mradmin.web.Http;
import at.marcelr.mradmin.web.WebServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.jspecify.annotations.Nullable;

/**
 * The map of the player's own page (tab "Map"): MRadmin's own map view over the pictures squaremap renders, with the
 * layers the modules offer this player ({@link WebModule#selfMap}) and their right-click actions.
 * <pre>
 *  GET  /api/self/map?lang=de                               worlds, layers and what the page offers (page)
 *  GET  /api/self/map/worlds                                only the worlds (small maps on the pages, e.g. a loader)
 *  GET  /api/self/map/players                               own position and the other players shown
 *  GET  /api/self/map/tiles/{world}/{zoom}/{tile}           one map picture (x_z.png) from squaremap's folder
 *  GET  /api/self/map/stamps?world=&zoom=&tiles=x_z,…       when those pictures were last drawn
 *  POST /api/self/map/action {module, layer, action, args, lang}   an action -> 400 {message} on refusal
 *  POST /api/self/map/hide {hidden}                         hide oneself on the map (and see no one else)
 * </pre>
 * squaremap's own web server and page are not needed for this - MRadmin reads the pictures from its folder.
 * Players on the map, the chunk grid, opening at the player and following are set in {@code map.player-page}.
 */
final class SelfMap {

    /** Who may see the other online players on the map. */
    static final String PERM_PLAYERS = "mradmin.map.players";
    /** Players who hid themselves on the map (they see no one else either). */
    private static final String HIDDEN_FILE = "map-hidden";

    /** Positions of all online players, taken at most every 0.4 s (every open map asks about once a second). */
    private volatile List<Spot> spots = List.of();
    private volatile long spotsAt;

    /** One online player as the map sees them; {@code hidden} = never shown to others. */
    private record Spot(UUID uuid, String name, String world, double x, double z, float yaw, boolean hidden) {
    }

    private final WebServer server;
    private final MRAdmin plugin;
    private final SelfRoutes routes;
    private final java.util.Set<UUID> hiddenSelf = new java.util.HashSet<>();
    private boolean hiddenLoaded;

    SelfMap(WebServer server, SelfRoutes routes) {
        this.server = server;
        this.plugin = server.plugin();
        this.routes = routes;
    }

    void register() {
        server.self("GET", "/api/self/map", this::map);
        server.self("GET", "/api/self/map/worlds", this::worlds);
        server.self("GET", "/api/self/map/tiles/{world}/{zoom}/{tile}", this::tile);
        server.self("GET", "/api/self/map/stamps", this::stamps);
        server.self("GET", "/api/self/map/players", this::players);
        server.self("POST", "/api/self/map/action", this::action);
        server.self("POST", "/api/self/map/hide", this::hide);
    }

    /** Whether there is a map (squaremap renders, at least one world). */
    boolean available() {
        return plugin.map() != null && plugin.map().tilesDir() != null;
    }

    /** The worlds with pictures, without the layers - for small maps on the pages. Empty without a map. */
    private void worlds(Http http) throws Exception {
        routes.check(http);
        List<Map<String, Object>> worlds = available() ? server.sync(() -> plugin.map().worlds()) : List.of();
        http.json(200, Map.of("worlds", worlds));
    }

    private void map(Http http) throws Exception {
        routes.check(http);
        if (!available()) {
            throw new Http.WebException(404, "map-off");
        }
        List<Map<String, Object>> worlds = server.sync(() -> plugin.map().worlds());
        List<Map<String, Object>> layers = new ArrayList<>();
        for (var entry : plugin.modules().webModules().entrySet()) {
            SelfViewer viewer = routes.viewer(http, entry.getKey());
            AdminModule module = plugin.modules().module(entry.getKey());
            List<SelfMapLayer> list = server.sync(() -> plugin.modules().call(module,
                    () -> entry.getValue().selfMap(viewer), List.<SelfMapLayer>of()));
            String fallback = module == null ? entry.getKey() : module.plugin().getName();
            for (SelfMapLayer layer : list) {
                layers.add(layer(entry.getKey(), fallback, layer));
            }
        }
        MapSettings.PlayerPage page = plugin.map().settings().page();
        Map<String, Object> offers = new LinkedHashMap<>();
        offers.put("openAtPlayer", page.openAtPlayer());
        offers.put("follow", page.follow());
        offers.put("chunkGrid", page.chunkGrid());
        offers.put("self", page.self());
        offers.put("others", page.others() && server.permissions().has(http.player().player(), PERM_PLAYERS));
        offers.put("positions", page.positions());
        offers.put("updateSeconds", page.updateSeconds());
        offers.put("allowHide", page.allowHide());
        offers.put("hidden", hiddenOnMap().contains(http.player().player()));
        http.json(200, Map.of("worlds", worlds, "layers", layers, "page", offers));
    }

    private Map<String, Object> layer(String module, String fallbackGroup, SelfMapLayer layer) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("module", module);
        l.put("group", layer.group() == null || layer.group().isBlank() ? fallbackGroup : layer.group());
        l.put("id", layer.id());
        l.put("name", layer.name());
        l.put("color", layer.color());
        List<Map<String, Object>> markers = new ArrayList<>();
        for (SelfMapLayer.Marker m : layer.markers()) {
            Map<String, Object> marker = new LinkedHashMap<>();
            marker.put("id", m.id());
            // With a height (API 10) a marker in the Nether belongs to the ceiling map or to the one below.
            marker.put("world", m.y() == null || plugin.map() == null ? m.world() : plugin.map().mapKey(m.world(), m.y()));
            marker.put("x", m.x());
            marker.put("z", m.z());
            marker.put("width", m.width());
            marker.put("length", m.length());
            marker.put("label", m.label());
            marker.put("color", m.color());
            marker.put("details", m.details());
            marker.put("actions", actions(m.actions()));
            markers.add(marker);
        }
        l.put("markers", markers);
        l.put("here", actions(layer.here()));
        if (layer.toggle() != null) {
            l.put("toggle", Map.of("label", layer.toggle().label(), "on", layer.toggle().on(),
                    "action", layer.toggle().action()));
        }
        return l;
    }

    private static List<Map<String, Object>> actions(List<SelfMapLayer.Action> actions) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SelfMapLayer.Action a : actions) {
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("id", a.id());
            action.put("label", a.label());
            action.put("tone", a.tone());
            action.put("icon", a.icon() == null ? "" : a.icon());
            if (a.confirm() != null) {
                action.put("confirm", a.confirm());
            }
            if (a.input() != null) {
                action.put("input", Map.of("label", a.input().label(), "value", a.input().value(),
                        "maxLength", a.input().maxLength()));
            }
            if (a.choice() != null && !a.choice().options().isEmpty()) {
                List<Map<String, Object>> options = new ArrayList<>();
                for (SelfMapLayer.Option o : a.choice().options()) {
                    options.add(Map.of("id", o.id(), "label", o.label(), "color", o.color() == null ? "" : o.color()));
                }
                action.put("choice", Map.of("label", a.choice().label(),
                        "value", a.choice().value() == null ? "" : a.choice().value(), "options", options));
            }
            list.add(action);
        }
        return list;
    }

    /**
     * Where the player stands (while online: to open the map there and to follow) and the other players they may
     * see. Left out for others: players in admin mode, spectators and invisible ones (each per config), bots and
     * everyone in a hidden world. The player always gets their own position.
     */
    private void players(Http http) throws Exception {
        routes.check(http);
        if (!available()) {
            throw new Http.WebException(404, "map-off");
        }
        MapSettings.PlayerPage page = plugin.map().settings().page();
        UUID me = http.player().player();
        List<Spot> all = page.positions() ? spots(page) : List.of();
        Set<UUID> hiddenNow = hiddenOnMap();
        // Who hid themselves sees no one else either.
        boolean others = page.others() && server.permissions().has(me, PERM_PLAYERS) && !hiddenNow.contains(me);
        Map<String, Object> self = null;
        List<Map<String, Object>> list = new ArrayList<>();
        for (Spot s : all) {
            if (s.uuid().equals(me)) {
                self = spot(s);
            } else if (others && !s.hidden() && !hiddenNow.contains(s.uuid()) && !page.hiddenWorlds().contains(baseKey(s.world()))) {
                list.add(spot(s));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("self", self);
        result.put("players", list);
        // When the positions were taken (server clock): the browser plays them back evenly instead of jerking.
        result.put("at", spotsAt);
        http.json(200, result);
    }

    /** The player hides themselves on the map - or shows themselves again (map.player-page.players.allow-hide). */
    private void hide(Http http) throws Exception {
        routes.check(http);
        if (!plugin.map().settings().page().allowHide()) {
            throw new Http.WebException(403, "forbidden");
        }
        boolean hide = "true".equals(http.string("hidden"));
        UUID me = http.player().player();
        synchronized (hiddenSelf) {
            hiddenOnMap();
            if (hide ? hiddenSelf.add(me) : hiddenSelf.remove(me)) {
                YamlConfiguration y = new YamlConfiguration();
                y.options().setHeader(List.of("MRadmin - players who hid themselves on the map of the player page "
                        + "(managed by the plugin)."));
                y.set("players", hiddenSelf.stream().map(UUID::toString).toList());
                plugin.storage().saveYaml(HIDDEN_FILE, y);
            }
        }
        http.json(200, Map.of("hidden", hide));
    }

    /** Who hid themselves - nobody while the setting is off. */
    private Set<UUID> hiddenOnMap() {
        if (!plugin.map().settings().page().allowHide()) {
            return Set.of();
        }
        synchronized (hiddenSelf) {
            if (!hiddenLoaded) {
                YamlConfiguration y = plugin.storage().loadYaml(HIDDEN_FILE, plugin.getLogger());
                for (String raw : y.getStringList("players")) {
                    try {
                        hiddenSelf.add(UUID.fromString(raw));
                    } catch (IllegalArgumentException ignored) {
                        // broken entry
                    }
                }
                hiddenLoaded = true;
            }
            return Set.copyOf(hiddenSelf);
        }
    }

    /** The dimension of a map key (the Nether ceiling belongs to the Nether, e.g. for hidden-worlds). */
    private static String baseKey(String mapKey) {
        return mapKey.endsWith(MapService.ROOF_KEY) ? mapKey.substring(0, mapKey.length() - MapService.ROOF_KEY.length())
                : mapKey;
    }

    private List<Spot> spots(MapSettings.PlayerPage page) throws Exception {
        long now = System.currentTimeMillis();
        if (now - spotsAt < 400) {
            return spots;
        }
        List<Spot> list = server.sync(() -> {
            List<Spot> found = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (Npcs.is(p)) {
                    continue;
                }
                boolean hidden = (page.hideAdminMode() && plugin.adminMode().active(p))
                        || (page.hideSpectators() && p.getGameMode() == GameMode.SPECTATOR)
                        || (page.hideInvisible() && (p.isInvisible() || p.hasPotionEffect(PotionEffectType.INVISIBILITY)));
                var l = p.getLocation();
                // On the Nether ceiling the player belongs to the ceiling map (the map switches to it by itself).
                found.add(new Spot(p.getUniqueId(), p.getName(), plugin.map().mapKey(p.getWorld(), l.getY()),
                        l.getX(), l.getZ(), l.getYaw(), hidden));
            }
            return found;
        });
        spots = List.copyOf(list);
        spotsAt = now;
        return spots;
    }

    private static Map<String, Object> spot(Spot s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uuid", s.uuid().toString());
        m.put("name", s.name());
        m.put("world", s.world());
        m.put("x", Math.round(s.x() * 10) / 10.0);
        m.put("z", Math.round(s.z() * 10) / 10.0);
        m.put("yaw", Math.round(s.yaw()));
        return m;
    }

    /**
     * One picture - straight from squaremap's folder (the Nether ceiling from MRadmin's own). With {@code ?v=} (the
     * picture's time from {@link #stamps}) the browser may keep it for a day - a newer picture gets a new address.
     */
    private void tile(Http http) throws Exception {
        routes.check(http);
        Path file = picture(http.param("world"), http.param("zoom"), http.param("tile"));
        if (file == null || !Files.isRegularFile(file)) {
            throw new Http.WebException(404, "not-found");
        }
        boolean versioned = http.query().containsKey("v");
        http.send(200, "image/png", Files.readAllBytes(file), versioned ? "private, max-age=86400" : "private, max-age=30");
    }

    /**
     * When the pictures the browser shows were last drawn ({@code ?world=&zoom=&tiles=x_z,x_z}): {"x_z": seconds, 0 =
     * none}. The map asks every 15 seconds and loads only the pictures that changed since.
     */
    private void stamps(Http http) throws Exception {
        routes.check(http);
        Map<String, String> q = http.query();
        String[] names = q.getOrDefault("tiles", "").split(",");
        if (names.length > 200) {
            throw new Http.WebException(400, "too-many");
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (String name : names) {
            if (name.isEmpty()) {
                continue;
            }
            Path file = picture(q.getOrDefault("world", ""), q.getOrDefault("zoom", ""), name + ".png");
            if (file == null) {
                throw new Http.WebException(404, "not-found");
            }
            result.put(name, Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() / 1000 : 0L);
        }
        http.json(200, result);
    }

    /** The file of a picture, the names checked so nothing outside the folders can be read; null if invalid. */
    private @Nullable Path picture(String world, String zoom, String tile) {
        Path tiles = plugin.map() == null ? null : plugin.map().tilesDir();
        if (tiles == null || !world.matches("[a-z0-9_.-]{1,64}") || !zoom.matches("\\d{1,2}")
                || !tile.matches("-?\\d{1,6}_-?\\d{1,6}\\.png")) {
            return null;
        }
        // The Nether ceiling comes from MRadmin's own pictures, everything else from squaremap.
        if (world.endsWith("-roof")) {
            tiles = plugin.map().roofDir();
            if (tiles == null) {
                return null;
            }
        }
        Path file = tiles.resolve(world).resolve(zoom).resolve(tile);
        return file.normalize().startsWith(tiles.normalize()) ? file : null;
    }


    private void action(Http http) throws Exception {
        routes.check(http);
        if (!server.allow("self-action:" + http.player().player(), 60)) {
            throw new Http.WebException(429, "rate-limit");
        }
        String moduleId = http.string("module");
        WebModule web = moduleId == null ? null : plugin.modules().web(moduleId);
        AdminModule module = moduleId == null ? null : plugin.modules().module(moduleId);
        String layer = http.string("layer");
        String action = http.string("action");
        if (web == null || module == null || layer == null || action == null || action.isBlank()) {
            throw new Http.WebException(404, "not-found");
        }
        Map<String, String> args = new LinkedHashMap<>();
        if (http.body().get("args") instanceof com.google.gson.JsonObject o) {
            o.entrySet().forEach(e -> {
                if (e.getValue().isJsonPrimitive() && args.size() < 20) {
                    String v = e.getValue().getAsString();
                    args.put(e.getKey(), v.length() > 200 ? v.substring(0, 200) : v);
                }
            });
        }
        SelfViewer viewer = routes.viewer(http, moduleId);
        String refused = server.sync(() -> plugin.modules().attempt(module,
                () -> web.selfMapAction(viewer, layer, action, args)));
        if (refused != null) {
            http.json(400, Map.of("error", "module", "message", refused));
            return;
        }
        plugin.getLogger().info(viewer.name() + " (own page, map) " + module.id() + "/" + layer + ": " + action + ".");
    }
}

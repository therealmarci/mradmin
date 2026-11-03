package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.api.AdminModule;
import at.marcelr.mradmin.api.SelfMapLayer;
import at.marcelr.mradmin.api.SelfViewer;
import at.marcelr.mradmin.api.WebModule;
import at.marcelr.mradmin.web.Http;
import at.marcelr.mradmin.web.WebServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The map of the player's own page (tab "Map"): MRadmin's own map view over the pictures squaremap renders, with the
 * layers the modules offer this player ({@link WebModule#selfMap}) and their right-click actions.
 * <pre>
 *  GET  /api/self/map?lang=de                               worlds and layers
 *  GET  /api/self/map/tiles/{world}/{zoom}/{tile}           one map picture (x_z.png) from squaremap's folder
 *  POST /api/self/map/action {module, layer, action, args, lang}   an action -> 400 {message} on refusal
 * </pre>
 * squaremap's own web server and page are not needed for this - MRadmin reads the pictures from its folder.
 */
final class SelfMap {

    private final WebServer server;
    private final MRAdmin plugin;
    private final SelfRoutes routes;

    SelfMap(WebServer server, SelfRoutes routes) {
        this.server = server;
        this.plugin = server.plugin();
        this.routes = routes;
    }

    void register() {
        server.self("GET", "/api/self/map", this::map);
        server.self("GET", "/api/self/map/tiles/{world}/{zoom}/{tile}", this::tile);
        server.self("POST", "/api/self/map/action", this::action);
    }

    /** Whether there is a map (squaremap renders, at least one world). */
    boolean available() {
        return plugin.map() != null && plugin.map().tilesDir() != null;
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
            for (SelfMapLayer layer : list) {
                layers.add(layer(entry.getKey(), layer));
            }
        }
        http.json(200, Map.of("worlds", worlds, "layers", layers));
    }

    private static Map<String, Object> layer(String module, SelfMapLayer layer) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("module", module);
        l.put("id", layer.id());
        l.put("name", layer.name());
        l.put("color", layer.color());
        List<Map<String, Object>> markers = new ArrayList<>();
        for (SelfMapLayer.Marker m : layer.markers()) {
            Map<String, Object> marker = new LinkedHashMap<>();
            marker.put("id", m.id());
            marker.put("world", m.world());
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
            if (a.confirm() != null) {
                action.put("confirm", a.confirm());
            }
            if (a.input() != null) {
                action.put("input", Map.of("label", a.input().label(), "value", a.input().value(),
                        "maxLength", a.input().maxLength()));
            }
            list.add(action);
        }
        return list;
    }

    /** One picture - straight from squaremap's folder, the names checked so nothing outside it can be read. */
    private void tile(Http http) throws Exception {
        routes.check(http);
        String world = http.param("world");
        String zoom = http.param("zoom");
        String tile = http.param("tile");
        Path tiles = plugin.map() == null ? null : plugin.map().tilesDir();
        if (tiles == null || !world.matches("[a-z0-9_.-]{1,64}") || !zoom.matches("\\d{1,2}")
                || !tile.matches("-?\\d{1,6}_-?\\d{1,6}\\.png")) {
            throw new Http.WebException(404, "not-found");
        }
        Path file = tiles.resolve(world).resolve(zoom).resolve(tile);
        if (!file.normalize().startsWith(tiles.normalize()) || !Files.isRegularFile(file)) {
            throw new Http.WebException(404, "not-found");
        }
        // squaremap renders again every few seconds - the browser may keep a picture only briefly.
        http.send(200, "image/png", Files.readAllBytes(file), "private, max-age=30");
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

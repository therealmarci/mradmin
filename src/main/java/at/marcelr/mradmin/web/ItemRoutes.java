package at.marcelr.mradmin.web;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.items.ItemCatalog;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Items for every page that shows or edits them (see {@link at.marcelr.mradmin.items.ItemAssets}).
 * <pre>
 *  GET /api/items/catalog        what the item editor offers + where each item's picture is in the atlas
 *  GET /api/items/atlas.png      all item pictures in one image (cached for good - the catalog names its version)
 *  GET /api/items/names/{lang}   item, enchantment and effect names in "de" or "en" (the game's own)
 *  POST /api/items/preview {item}  the item as the server builds it from the editor's data (checks it, nothing is stored)
 * </pre>
 */
public final class ItemRoutes {

    private final WebServer server;
    private final MRAdmin plugin;
    private volatile Map<String, Object> catalog;

    private ItemRoutes(WebServer server) {
        this.server = server;
        this.plugin = server.plugin();
    }

    public static void register(WebServer server) {
        ItemRoutes r = new ItemRoutes(server);
        server.route("GET", "/api/items/catalog", r::catalog);
        server.route("GET", "/api/items/atlas.png", r::atlas);
        server.route("GET", "/api/items/names/{lang}", r::names);
        server.route("POST", "/api/items/preview", r::preview);
    }

    private void catalog(Http http) throws Exception {
        if (catalog == null) {
            // The registries do not change while the server runs.
            catalog = server.sync(ItemCatalog::build);
        }
        Map<String, Object> answer = new LinkedHashMap<>(catalog);
        answer.put("icons", plugin.items().info());
        http.json(200, answer);
    }

    private void atlas(Http http) throws IOException {
        byte[] png = plugin.items().atlas();
        if (png == null) {
            throw new Http.WebException(404, "not-found");
        }
        http.send(200, "image/png", png, "private, max-age=31536000, immutable");
    }

    private void preview(Http http) throws Exception {
        if (!(http.bodyTree().get("item") instanceof Map<?, ?> raw)) {
            throw new Http.WebException(400, "item-type");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) raw;
        var items = plugin.modules().items();
        Object result = server.sync(() -> {
            try {
                return items.describe(items.build(data));
            } catch (IllegalArgumentException e) {
                return e.getMessage();
            }
        });
        if (result instanceof String error) {
            throw new Http.WebException(400, error);
        }
        http.json(200, Map.of("item", result));
    }

    private void names(Http http) throws IOException {
        byte[] json = plugin.items().names(http.param("lang"));
        if (json == null) {
            throw new Http.WebException(404, "not-found");
        }
        http.send(200, "application/json; charset=utf-8", json, "private, max-age=86400");
    }
}

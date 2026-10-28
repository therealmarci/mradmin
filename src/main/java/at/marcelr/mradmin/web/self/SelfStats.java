package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.api.AdminModule;
import at.marcelr.mradmin.api.SelfRanking;
import at.marcelr.mradmin.web.Http;
import at.marcelr.mradmin.web.WebServer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The statistics page of the player area: rankings of the game's own statistics ({@link VanillaStats}) and of the
 * plugins ({@link at.marcelr.mradmin.api.WebModule#rankings}). Players can hide themselves from every ranking.
 * <pre>
 *  GET  /api/self/stats?lang=de          the rankings: top places plus the viewer's own place
 *  POST /api/self/stats/hide  {hidden}   hide from (or show again in) all rankings
 * </pre>
 */
public final class SelfStats {

    private static final String FILE = "ranking-hidden";

    private final WebServer server;
    private final MRAdmin plugin;
    private final SelfRoutes routes;
    private final VanillaStats vanilla;
    private final Set<UUID> hidden = new HashSet<>();
    private boolean loaded;

    SelfStats(WebServer server, SelfRoutes routes) {
        this.server = server;
        this.plugin = server.plugin();
        this.routes = routes;
        this.vanilla = new VanillaStats(plugin);
    }

    void register() {
        server.self("GET", "/api/self/stats", this::stats);
        server.self("POST", "/api/self/stats/hide", this::hide);
    }

    boolean available() {
        return plugin.settings().playerWeb().rankings();
    }

    private void stats(Http http) throws Exception {
        routes.check(http);
        if (!available()) {
            throw new Http.WebException(404, "not-found");
        }
        UUID viewer = http.player().player();
        String lang = "de".equals(http.query().get("lang")) ? "de" : "en";
        int top = plugin.settings().playerWeb().rankingTop();
        Set<UUID> hiddenNow = hidden();
        List<Map<String, Object>> rankings = new ArrayList<>();
        for (String key : plugin.settings().playerWeb().rankingKinds()) {
            VanillaStats.Kind kind = VanillaStats.Kind.byKey(key);
            if (kind == null) {
                continue;
            }
            List<Place> places = new ArrayList<>();
            for (VanillaStats.Entry e : vanilla.ranking(kind)) {
                places.add(new Place(e.player(), e.name(), VanillaStats.format(kind, e.value(), lang)));
            }
            rankings.add(ranking("game:" + kind.key, null, places, viewer, hiddenNow, top));
        }
        for (var entry : plugin.modules().webModules().entrySet()) {
            AdminModule module = plugin.modules().module(entry.getKey());
            List<SelfRanking> list = server.sync(() -> plugin.modules().call(module,
                    () -> entry.getValue().rankings(lang), List.<SelfRanking>of()));
            for (SelfRanking r : list) {
                List<Place> places = new ArrayList<>();
                for (SelfRanking.Entry e : r.entries()) {
                    places.add(new Place(e.player(), e.name(), e.value()));
                }
                rankings.add(ranking(entry.getKey() + ":" + r.id(), r.title(), places, viewer, hiddenNow, top));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hidden", hiddenNow.contains(viewer));
        result.put("rankings", rankings);
        http.json(200, result);
    }

    private record Place(UUID player, String name, String value) {
    }

    /** Top places without hidden players, numbered again, plus the viewer's own place if it is further down. */
    private static Map<String, Object> ranking(String id, String title, List<Place> all, UUID viewer, Set<UUID> hidden,
                                               int top) {
        List<Map<String, Object>> places = new ArrayList<>();
        Map<String, Object> own = null;
        int rank = 0;
        for (Place p : all) {
            if (hidden.contains(p.player())) {
                continue;
            }
            rank++;
            boolean self = p.player().equals(viewer);
            if (rank <= top) {
                places.add(place(rank, p, self));
            } else if (self) {
                own = place(rank, p, true);
            }
            if (rank > top && own != null) {
                break;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("places", places);
        m.put("own", own);
        return m;
    }

    private static Map<String, Object> place(int rank, Place p, boolean self) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rank", rank);
        m.put("uuid", p.player().toString());
        m.put("name", p.name());
        m.put("value", p.value());
        m.put("self", self);
        return m;
    }

    private void hide(Http http) throws Exception {
        routes.check(http);
        boolean hide = "true".equals(http.string("hidden"));
        UUID viewer = http.player().player();
        synchronized (this) {
            hidden();
            if (hide ? hidden.add(viewer) : hidden.remove(viewer)) {
                YamlConfiguration y = new YamlConfiguration();
                y.options().setHeader(List.of("MRadmin - players hidden from the rankings of the web interface (managed by the plugin)."));
                y.set("players", hidden.stream().map(UUID::toString).toList());
                plugin.storage().saveYaml(FILE, y);
            }
        }
    }

    private synchronized Set<UUID> hidden() {
        if (!loaded) {
            YamlConfiguration y = plugin.storage().loadYaml(FILE, plugin.getLogger());
            for (String raw : y.getStringList("players")) {
                try {
                    hidden.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                    // broken entry
                }
            }
            loaded = true;
        }
        return Set.copyOf(hidden);
    }
}

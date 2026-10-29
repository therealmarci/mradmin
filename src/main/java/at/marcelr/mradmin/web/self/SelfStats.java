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
 *  GET  /api/self/stats?lang=de          personal (own values and places), totals of the server, the rankings (top
 *                                        places plus the viewer's own place)
 *  POST /api/self/stats/hide  {hidden}   hide from (or show again in) all rankings
 * </pre>
 */
public final class SelfStats {

    private static final String FILE = "ranking-hidden";
    /** Places in the table view of a ranking at most. */
    private static final int ALL_MAX = 1000;

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

    /**
     * The viewer's own game statistics for the tile on the start page (player-web.personal-stats, in that order):
     * [{key, value}] with the value formatted for the language; empty = no tile.
     */
    List<Map<String, Object>> personal(UUID player, String lang) throws Exception {
        vanilla.live(server.sync(VanillaStats::readLive));
        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : plugin.settings().playerWeb().personalStats()) {
            VanillaStats.Kind kind = VanillaStats.Kind.byKey(key);
            if (kind != null) {
                result.add(Map.of("key", kind.key, "value", VanillaStats.format(kind, vanilla.of(player, kind), lang)));
            }
        }
        return result;
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
        List<Map<String, Object>> personal = new ArrayList<>();
        List<Map<String, Object>> totals = new ArrayList<>();
        List<String> shown = plugin.settings().playerWeb().rankingKinds();
        // Online players with their values right now (the files are only written when the server saves).
        vanilla.live(server.sync(VanillaStats::readLive));
        // The game's statistics: the viewer's own value and place for every kind, rankings and totals for the
        // configured ones (player-web.rankings.statistics).
        for (VanillaStats.Kind kind : VanillaStats.Kind.values()) {
            List<VanillaStats.Entry> all = vanilla.ranking(kind);
            List<Place> places = new ArrayList<>();
            double sum = 0;
            for (VanillaStats.Entry e : all) {
                places.add(new Place(e.player(), e.name(), VanillaStats.format(kind, e.value(), lang), e.value()));
                sum += e.value();
            }
            String id = "game:" + kind.key;
            personal.add(own(id, null, places, viewer, hiddenNow,
                    VanillaStats.format(kind, vanilla.of(viewer, kind), lang), vanilla.of(viewer, kind)));
            if (shown.stream().anyMatch(k -> kind.key.equalsIgnoreCase(k.trim()))) {
                rankings.add(ranking(id, null, places, viewer, hiddenNow, top));
                totals.add(Map.of("id", id, "value", VanillaStats.format(kind, sum, lang)));
            }
        }
        // The plugins' rankings (e.g. duels won).
        for (var entry : plugin.modules().webModules().entrySet()) {
            AdminModule module = plugin.modules().module(entry.getKey());
            List<SelfRanking> list = server.sync(() -> plugin.modules().call(module,
                    () -> entry.getValue().rankings(lang), List.<SelfRanking>of()));
            for (SelfRanking r : list) {
                List<Place> places = new ArrayList<>();
                for (SelfRanking.Entry e : r.entries()) {
                    places.add(new Place(e.player(), e.name(), e.value(), Double.NaN));
                }
                String id = entry.getKey() + ":" + r.id();
                rankings.add(ranking(id, r.title(), places, viewer, hiddenNow, top));
                Place mine = places.stream().filter(p -> p.player().equals(viewer)).findFirst().orElse(null);
                if (mine != null) {
                    personal.add(own(id, r.title(), places, viewer, hiddenNow, mine.value(), Double.NaN));
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hidden", hiddenNow.contains(viewer));
        result.put("players", vanilla.players());
        result.put("personal", personal);
        result.put("totals", totals);
        result.put("rankings", rankings);
        http.json(200, result);
    }

    /** One value and amount for sorting (NaN for the plugins' rankings, which only give text). */
    private record Place(UUID player, String name, String value, double amount) {
    }

    /**
     * The viewer's own value with their place among the players shown in the ranking (the viewer counts even if they
     * hid themselves), how many there are, the best value and - for the game's statistics - the share of the best.
     */
    private static Map<String, Object> own(String id, String title, List<Place> all, UUID viewer, Set<UUID> hidden,
                                           String value, double amount) {
        int rank = 0;
        int total = 0;
        Place best = null;
        for (Place p : all) {
            boolean self = p.player().equals(viewer);
            if (hidden.contains(p.player()) && !self) {
                continue;
            }
            total++;
            if (best == null) {
                best = p;
            }
            if (self) {
                rank = total;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("value", value);
        m.put("rank", rank);
        m.put("total", rank == 0 ? total + 1 : total);
        m.put("best", best == null ? null : best.value());
        m.put("bestName", best == null ? null : best.name());
        boolean measurable = !Double.isNaN(amount) && best != null && best.amount() > 0;
        m.put("share", measurable ? Math.min(1, amount / best.amount()) : null);
        return m;
    }

    /**
     * Top places without hidden players, numbered again, plus the viewer's own place if it is further down, and all
     * places (up to 1000) for the table view.
     */
    private static Map<String, Object> ranking(String id, String title, List<Place> all, UUID viewer, Set<UUID> hidden,
                                               int top) {
        List<Map<String, Object>> places = new ArrayList<>();
        List<Map<String, Object>> everyone = new ArrayList<>();
        Map<String, Object> own = null;
        int rank = 0;
        for (Place p : all) {
            if (hidden.contains(p.player())) {
                continue;
            }
            rank++;
            boolean self = p.player().equals(viewer);
            Map<String, Object> place = place(rank, p, self);
            if (rank <= top) {
                places.add(place);
            } else if (self) {
                own = place;
            }
            // Everyone for the table view (a few thousand at most).
            if (rank <= ALL_MAX) {
                everyone.add(place);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("places", places);
        m.put("own", own);
        m.put("all", everyone);
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

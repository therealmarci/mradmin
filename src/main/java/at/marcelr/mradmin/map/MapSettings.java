package at.marcelr.mradmin.map;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The section {@code map} of config.yml: the web map squaremap and the plugins' layers on it.
 *
 * @param hideModuleWorlds keep worlds a module owns (e.g. MRpvp's arena world) off the map
 * @param offLayers        layers that are not drawn, as "module:layer"
 * @param hiddenLayers     layers that are hidden when the map opens, as "module:layer"
 */
public record MapSettings(boolean enabled, boolean autoConfigure, boolean hidePlayers, boolean hideModuleWorlds,
                          int updateSeconds, String address, boolean reverseProxy, boolean detectPublicIp,
                          Set<String> offLayers, Set<String> hiddenLayers, Grid grid) {

    /** The chunk grid MRadmin adds to squaremap (map.chunk-grid). */
    public record Grid(boolean enabled, boolean defaultVisible, String color, double opacity, int minChunkPixels,
                       boolean regionLines, String regionColor, boolean labels) {
    }

    public static MapSettings from(FileConfiguration c) {
        return new MapSettings(
                c.getBoolean("map.enabled", true),
                c.getBoolean("map.auto-configure", true),
                c.getBoolean("map.hide-players", true),
                c.getBoolean("map.hide-module-worlds", true),
                Math.clamp(c.getInt("map.update-seconds", 60), 5, 3600),
                c.getString("map.address", "").trim(),
                c.getBoolean("map.reverse-proxy", false),
                c.getBoolean("map.detect-public-ip", true),
                layers(c, "map.layers.off"),
                layers(c, "map.layers.hidden"),
                new Grid(
                        c.getBoolean("map.chunk-grid.enabled", true),
                        c.getBoolean("map.chunk-grid.default-visible", true),
                        hex(c.getString("map.chunk-grid.color", "#FFFFFF"), "#FFFFFF"),
                        Math.clamp(c.getDouble("map.chunk-grid.opacity", 0.35), 0.0, 1.0),
                        Math.clamp(c.getInt("map.chunk-grid.min-chunk-pixels", 8), 2, 64),
                        c.getBoolean("map.chunk-grid.region-lines", true),
                        hex(c.getString("map.chunk-grid.region-color", "#FFD54F"), "#FFD54F"),
                        c.getBoolean("map.chunk-grid.labels", true)));
    }

    private static Set<String> layers(FileConfiguration c, String path) {
        return c.getStringList(path).stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** "#RRGGBB" for the grid script; anything else becomes the default. */
    private static String hex(String value, String fallback) {
        String v = value == null ? "" : value.trim();
        return v.matches("#[0-9a-fA-F]{6}") ? v : fallback;
    }
}

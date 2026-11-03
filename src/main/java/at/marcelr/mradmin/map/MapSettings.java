package at.marcelr.mradmin.map;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * The section {@code map} of config.yml: squaremap renders the pictures of the map of the player page.
 *
 * @param autoConfigure    keep squaremap's config fitting (own web server off, ...)
 * @param hideModuleWorlds keep worlds a module owns (e.g. MRpvp's arena world) off the map
 * @param autoInstall      install squaremap from Modrinth if it is missing
 * @param autoUpdate       download new squaremap versions (installed on the next start)
 * @param checkHours       how often Modrinth is asked
 */
public record MapSettings(boolean enabled, boolean autoConfigure, boolean hideModuleWorlds, boolean autoInstall,
                          boolean autoUpdate, int checkHours) {

    public static MapSettings from(FileConfiguration c) {
        return new MapSettings(
                c.getBoolean("map.enabled", true),
                c.getBoolean("map.auto-configure", true),
                c.getBoolean("map.hide-module-worlds", true),
                c.getBoolean("map.squaremap.auto-install", true),
                c.getBoolean("map.squaremap.auto-update", true),
                Math.clamp(c.getInt("map.squaremap.check-hours", 12), 1, 168));
    }
}

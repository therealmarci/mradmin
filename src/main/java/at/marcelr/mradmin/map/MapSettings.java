package at.marcelr.mradmin.map;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Set;

/**
 * The section {@code map} of config.yml: squaremap renders the pictures of the map of the player page.
 *
 * @param autoConfigure    keep squaremap's config fitting (own web server off, ...)
 * @param hideModuleWorlds keep worlds a module owns (e.g. MRpvp's arena world) off the map
 * @param autoInstall      install squaremap from Modrinth if it is missing
 * @param autoUpdate       download new squaremap versions (installed on the next start)
 * @param fullRender       render every world completely once after squaremap was installed or updated
 * @param checkHours       how often Modrinth is asked
 * @param page             the map of the player page ({@code map.player-page})
 * @param roof             the map of the Nether ceiling ({@code map.nether-roof})
 */
public record MapSettings(boolean enabled, boolean autoConfigure, boolean hideModuleWorlds, boolean autoInstall,
                          boolean autoUpdate, boolean fullRender, int checkHours, PlayerPage page, Roof roof) {

    /**
     * {@code map.nether-roof}: MRadmin's own map of the Nether ceiling.
     *
     * @param fromY           from this height a player is on the ceiling
     * @param fullRender      render all generated chunks once
     * @param chunksPerSecond pace of the complete render
     */
    public record Roof(boolean enabled, int fromY, boolean fullRender, int chunksPerSecond) {
    }

    /**
     * {@code map.player-page}: what the map of the player page offers besides the layers of the plugins.
     *
     * @param openAtPlayer   open the map where the player stands while they are online
     * @param follow         the button "follow" (keeps the player in the middle)
     * @param chunkGrid      the layer "chunk grid"
     * @param self           the player sees themselves
     * @param others         the player sees the other online players (also needs mradmin.map.players)
     * @param updateSeconds  how often the positions are asked while the map is open
     * @param hideAdminMode  players in admin mode are never shown to others
     * @param hideSpectators players in spectator mode are never shown to others
     * @param hideInvisible  invisible players are never shown to others
     * @param hiddenWorlds   world keys in which nobody is shown
     */
    public record PlayerPage(boolean openAtPlayer, boolean follow, boolean chunkGrid, boolean self, boolean others,
                             int updateSeconds, boolean hideAdminMode, boolean hideSpectators, boolean hideInvisible,
                             Set<String> hiddenWorlds) {

        /** Whether positions are needed at all (open at the player, follow or players on the map). */
        public boolean positions() {
            return openAtPlayer || follow || self || others;
        }
    }

    public static MapSettings from(FileConfiguration c) {
        String p = "map.player-page.";
        List<String> hidden = c.getStringList(p + "players.hidden-worlds");
        return new MapSettings(
                c.getBoolean("map.enabled", true),
                c.getBoolean("map.auto-configure", true),
                c.getBoolean("map.hide-module-worlds", true),
                c.getBoolean("map.squaremap.auto-install", true),
                c.getBoolean("map.squaremap.auto-update", true),
                c.getBoolean("map.squaremap.full-render", true),
                Math.clamp(c.getInt("map.squaremap.check-hours", 12), 1, 168),
                new PlayerPage(
                        c.getBoolean(p + "open-at-player", true),
                        c.getBoolean(p + "follow", true),
                        c.getBoolean(p + "chunk-grid", true),
                        c.getBoolean(p + "players.self", true),
                        c.getBoolean(p + "players.others", true),
                        Math.clamp(c.getInt(p + "players.update-seconds", 1), 1, 60),
                        c.getBoolean(p + "players.hide-admin-mode", true),
                        c.getBoolean(p + "players.hide-spectators", true),
                        c.getBoolean(p + "players.hide-invisible", true),
                        Set.copyOf(hidden)),
                new Roof(c.getBoolean("map.nether-roof.enabled", true),
                        Math.clamp(c.getInt("map.nether-roof.from-y", 128), 1, 320),
                        c.getBoolean("map.nether-roof.full-render", true),
                        Math.clamp(c.getInt("map.nether-roof.chunks-per-second", 40), 1, 1000)));
    }
}

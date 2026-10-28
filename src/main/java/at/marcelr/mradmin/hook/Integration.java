package at.marcelr.mradmin.hook;

import org.bukkit.Material;

import java.util.List;

/**
 * The MRtechnology plugins MRadmin knows by name - only for things that happen before or without their module: the
 * order of their dashboard buttons, the warning about a version from before the module API, the switch from old
 * configs (v7) and the icon of their tickets while they do not run. Everything else (button, permission, menus,
 * live information) comes from the plugin's module ({@link at.marcelr.mradmin.module.Modules}) - MRadmin names none
 * of their permissions.
 *
 * @param id      name under {@code integrations} in config.yml and the module id
 * @param plugin  plugin name as in its plugin.yml
 * @param command what the dashboard button runs for a plugin version whose module opens no menu itself (older ones)
 */
public record Integration(String id, String plugin, Material material, String command) {

    public static final List<Integration> ALL = List.of(
            new Integration("mrsleep", "MRsleep", Material.RED_BED, "mrsleep gui"),
            new Integration("mrchunks", "MRchunks", Material.EMERALD_BLOCK, "mrchunks gui"),
            new Integration("mrtoolbar", "MRtoolbar", Material.IRON_PICKAXE, "mrtoolbar gui"),
            new Integration("mrpvp", "MRpvp", Material.IRON_SWORD, "duel admin"));

    /** The integration for a plugin name (case-insensitive), or null. */
    public static Integration byPlugin(String plugin) {
        return ALL.stream().filter(i -> i.plugin().equalsIgnoreCase(plugin)).findFirst().orElse(null);
    }
}

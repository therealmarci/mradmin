package at.marcelr.mradmin.hook;

import org.bukkit.Material;

import java.util.List;

/**
 * Plugins MRadmin works together with out of the box. Each one is detected automatically: while it is installed and
 * enabled, the dashboard shows its button with live information, the player menu its player info, and (MRchunks) the
 * server health its conspicuous chunk loaders. Neither side needs the other - every plugin runs on its own.
 *
 * @param id         name under {@code integrations} in config.yml and in the language file (gui-hub-buttons.&lt;id&gt;)
 * @param plugin     plugin name as in its plugin.yml
 * @param command    run as the player when the button is clicked (the plugin checks its own permissions)
 * @param permission the button is only shown with this permission
 * @param hook       class of the plugin MRadmin talks to (see {@link PluginHooks})
 */
public record Integration(String id, String plugin, Material material, String command, String permission, String hook) {

    public static final List<Integration> ALL = List.of(
            new Integration("mrsleep", "MRsleep", Material.RED_BED, "mrsleep gui", "mrsleep.admin.gui",
                    "at.marcelr.mrsleep.api.MRadminHook"),
            new Integration("mrchunks", "MRchunks", Material.EMERALD_BLOCK, "mrchunks gui", "mrchunks.admin.gui",
                    "at.marcelr.mrchunks.api.MRadminHook"));

    /** The integration for a plugin name (case-insensitive), or null. */
    public static Integration byPlugin(String plugin) {
        return ALL.stream().filter(i -> i.plugin().equalsIgnoreCase(plugin)).findFirst().orElse(null);
    }
}

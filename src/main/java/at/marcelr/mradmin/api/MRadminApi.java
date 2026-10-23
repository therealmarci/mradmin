package at.marcelr.mradmin.api;

import at.marcelr.mradmin.MRAdmin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Public interface for other plugins, called by reflection (no build dependency needed):
 * <pre>
 * Plugin p = Bukkit.getPluginManager().getPlugin("MRadmin");
 * Class&lt;?&gt; api = p.getClass().getClassLoader().loadClass("at.marcelr.mradmin.api.MRadminApi");
 * boolean admin = (boolean) api.getMethod("adminMode", UUID.class).invoke(null, uuid);
 * </pre>
 * MRsleep uses it to leave players in admin mode out of the sleep percentage; MRpvp also sends live updates for the
 * web interface and alerts for the Discord admin log (API 2). Keep the signatures stable; new
 * methods raise {@link #API_VERSION}. Main thread only.
 */
public final class MRadminApi {

    public static final int API_VERSION = 2;

    private MRadminApi() {
    }

    /** Whether the online player with this id is in admin mode right now (false while MRadmin is disabled). */
    public static boolean adminMode(UUID player) {
        MRAdmin plugin = JavaPlugin.getPlugin(MRAdmin.class);
        if (!plugin.isEnabled() || plugin.adminMode() == null) {
            return false;
        }
        Player online = Bukkit.getPlayer(player);
        return online != null && plugin.adminMode().active(online);
    }

    /**
     * (API 2) Tells open pages of the web interface that something of a plugin changed - they load it again. Carries
     * no content, only plugin, type and id (e.g. "mrpvp", "game", "1042"); only members who may see that plugin's
     * page get it. Does nothing while the web interface is off.
     */
    public static void webEvent(String plugin, String type, String id) {
        MRAdmin admin = JavaPlugin.getPlugin(MRAdmin.class);
        if (admin.isEnabled() && admin.web() != null) {
            admin.web().events().plugin(plugin, type, id);
        }
    }

    /** (API 2) A line in the Discord admin log (event "plugin-alert", switchable in discord.events). */
    public static void adminLog(String plugin, String text) {
        MRAdmin admin = JavaPlugin.getPlugin(MRAdmin.class);
        if (admin.isEnabled()) {
            var integration = at.marcelr.mradmin.hook.Integration.byPlugin(plugin);
            admin.discord().log("plugin-alert", "plugin", integration == null ? plugin : integration.plugin(), "text", text);
        }
    }
}

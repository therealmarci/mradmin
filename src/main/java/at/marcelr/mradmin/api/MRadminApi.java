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
 * MRsleep uses it to leave players in admin mode out of the sleep percentage. Keep the signatures stable; new
 * methods raise {@link #API_VERSION}. Main thread only.
 */
public final class MRadminApi {

    public static final int API_VERSION = 1;

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
}

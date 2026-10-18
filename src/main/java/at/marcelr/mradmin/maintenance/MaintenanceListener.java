package at.marcelr.mradmin.maintenance;

import at.marcelr.mradmin.MRAdmin;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Instant;
import java.util.logging.Level;

/**
 * Keeps players without {@link MaintenanceManager#PERM_BYPASS} out during a maintenance:
 * <ol>
 *     <li>with LuckPerms already in the pre-login (the player never enters the world),</li>
 *     <li>otherwise - and as safety net - right when joining (the join message is suppressed).</li>
 * </ol>
 * Also shows the maintenance text in the server list.
 */
public final class MaintenanceListener implements Listener {

    private final MRAdmin plugin;
    private final boolean luckPerms;

    public MaintenanceListener(MRAdmin plugin) {
        this.plugin = plugin;
        this.luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
    }

    private MaintenanceManager manager() {
        return plugin.maintenance();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!manager().active() || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || !luckPerms) {
            return;
        }
        Boolean allowed;
        try {
            allowed = LuckPermsHook.check(event.getUniqueId(), MaintenanceManager.PERM_BYPASS);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not check the permissions of " + event.getName()
                    + " with LuckPerms, checking again after joining: " + e.getMessage());
            return;
        }
        if (allowed == null) {
            // Not set in LuckPerms: like on the server, the plugin.yml default (op) decides.
            allowed = Bukkit.getOfflinePlayer(event.getUniqueId()).isOp();
        }
        if (!allowed) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, manager().kickScreen());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (manager().active() && !manager().canJoin(event.getPlayer())) {
            event.joinMessage(null);
            event.getPlayer().kick(manager().kickScreen());
        } else if (manager().active()) {
            plugin.messages().send(event.getPlayer(), "maintenance-join-staff",
                    manager().resolvers(manager().current(), Instant.now()));
        }
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        var settings = plugin.settings().maintenance();
        if (!manager().active() || !settings.motd()) {
            return;
        }
        // Built per ping, so the countdown in the server list is always current.
        event.motd(plugin.messages().get(manager().openEnd() ? "maintenance-motd-open" : "maintenance-motd",
                manager().resolvers(manager().current(), Instant.now())));
        if (settings.replaceVersion()) {
            // A protocol that no client has makes the client show the version text instead of the player count.
            event.setProtocolVersion(-1);
            event.setVersion(LegacyComponentSerializer.legacySection().serialize(
                    plugin.messages().get("maintenance-version-text")));
        }
    }
}

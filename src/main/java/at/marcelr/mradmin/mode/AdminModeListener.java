package at.marcelr.mradmin.mode;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.gui.HubMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Join/quit handling, the teleport compass and the protections while a player is in admin mode. */
public final class AdminModeListener implements Listener {

    private final MRAdmin plugin;

    public AdminModeListener(MRAdmin plugin) {
        this.plugin = plugin;
    }

    private AdminModeManager mode() {
        return plugin.adminMode();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        mode().resume(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        mode().quit(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !mode().isCompass(event.getItem())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission(AdminModeManager.PERM_USE)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        new HubMenu(plugin, player).open();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (mode().isCompass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            return;
        }
        if (mode().active(event.getPlayer()) && plugin.settings().adminMode().preventDrop()) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "mode-no-drop");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && mode().active(player)
                && plugin.settings().adminMode().preventPickup()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!mode().active(event.getPlayer())) {
            return;
        }
        // Nothing of the admin inventory may end up in the world; the real items are safe in the saved state.
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }
}

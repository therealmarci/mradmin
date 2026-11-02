package at.marcelr.mradmin.mode;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.gui.HubMenu;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
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

    /**
     * World managers set the game mode of the world after every world change (Multiverse: enforce-gamemode). The admin
     * mode must survive that - the player would be vulnerable with the admin inventory. /gamemode stays possible.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        GameMode next = event.getNewGameMode();
        if (event.getCause() == PlayerGameModeChangeEvent.Cause.PLUGIN && next != GameMode.CREATIVE
                && next != GameMode.SPECTATOR && mode().holdsGameMode(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!mode().isCompass(event.getItem())) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Also in the off hand: the item may be a block and must never be placed.
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(AdminModeManager.PERM_USE)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        plugin.messages().run(player, () -> new HubMenu(plugin, player).open());
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

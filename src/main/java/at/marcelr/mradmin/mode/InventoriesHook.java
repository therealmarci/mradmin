package at.marcelr.mradmin.mode;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.mvplugins.multiverse.inventories.event.GameModeChangeShareHandlingEvent;
import org.mvplugins.multiverse.inventories.event.WorldChangeShareHandlingEvent;

/**
 * Multiverse-Inventories (optional, only registered if it is installed) stores the inventory per world group. In admin
 * mode the player carries the admin inventory, which must not end up in a group - and their own inventory comes back
 * from the admin mode file, so nothing may be swapped until the mode ends.
 */
public final class InventoriesHook implements Listener {

    private final AdminModeManager mode;

    public InventoriesHook(AdminModeManager mode) {
        this.mode = mode;
    }

    @EventHandler
    public void onWorldChange(WorldChangeShareHandlingEvent event) {
        if (mode.active(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onGameModeChange(GameModeChangeShareHandlingEvent event) {
        if (mode.active(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}

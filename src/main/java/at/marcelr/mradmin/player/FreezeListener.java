package at.marcelr.mradmin.player;

import at.marcelr.mradmin.MRAdmin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Keeps frozen players in place. Looking around stays allowed, so they are not disoriented. */
public final class FreezeListener implements Listener {

    private final MRAdmin plugin;

    public FreezeListener(MRAdmin plugin) {
        this.plugin = plugin;
    }

    private boolean frozen(Player player) {
        return plugin.freeze().frozen(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!frozen(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location stay = from.clone();
            stay.setYaw(to.getYaw());
            stay.setPitch(to.getPitch());
            event.setTo(stay);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // Ender pearls, chorus fruit, /spawn & co. - but staff teleporting them (PLUGIN/COMMAND by others) stays possible.
        if (frozen(event.getPlayer()) && (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        cancel(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            cancel(player, event);
        }
    }

    /** Frozen players cannot be hurt either - they cannot defend themselves. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && frozen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (frozen(event.getPlayer())) {
            plugin.messages().send(event.getPlayer(), "freeze-still");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.freeze().quit(event.getPlayer());
    }

    private void cancel(Player player, Cancellable event) {
        if (frozen(player)) {
            event.setCancelled(true);
        }
    }
}

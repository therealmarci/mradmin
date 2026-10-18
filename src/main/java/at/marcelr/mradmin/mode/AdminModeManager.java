package at.marcelr.mradmin.mode;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static at.marcelr.mradmin.Messages.tag;

/**
 * Admin mode: the player's state is saved to {@code admin-mode/<uuid>.yml} <b>before</b> anything is changed,
 * then they get the configured game mode, an empty inventory and the teleport compass. Turning it off restores the
 * saved state exactly and teleports back. Because the file is written first, a crash or restart never loses items:
 * the player simply is still in admin mode afterwards.
 */
public final class AdminModeManager {

    public static final String PERM_USE = "mradmin.mode";
    public static final String PERM_TELEPORT = "mradmin.mode.teleport";

    private final MRAdmin plugin;
    private final File folder;
    private final NamespacedKey compassKey;
    private final Map<UUID, SavedState> states = new ConcurrentHashMap<>();
    private BukkitTask compassTask;

    public AdminModeManager(MRAdmin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "admin-mode");
        this.compassKey = new NamespacedKey(plugin, "admin_compass");
    }

    public void start() {
        stop();
        // Players that are online (plugin reload) or come back later are picked up from their files.
        Bukkit.getOnlinePlayers().forEach(player -> resume(player, false));
        compassTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID id : states.keySet()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    ensureCompass(player);
                }
            }
        }, 40L, 40L);
    }

    public void stop() {
        if (compassTask != null) {
            compassTask.cancel();
            compassTask = null;
        }
    }

    private Settings.AdminModeSettings settings() {
        return plugin.settings().adminMode();
    }

    private Messages messages() {
        return plugin.messages();
    }

    public boolean active(Player player) {
        return states.containsKey(player.getUniqueId());
    }

    public Collection<SavedState> all() {
        return states.values();
    }

    private File file(UUID id) {
        return new File(folder, id + ".yml");
    }

    // ------------------------------------------------------------------- on/off

    public void enable(Player player) {
        if (active(player)) {
            messages().send(player, "mode-already-on");
            return;
        }
        SavedState state = SavedState.capture(player);
        try {
            if (!folder.exists() && !folder.mkdirs()) {
                throw new java.io.IOException("cannot create " + folder);
            }
            state.save(file(player.getUniqueId()));
        } catch (Exception e) {
            // Never touch the inventory if it could not be saved.
            plugin.getLogger().log(Level.SEVERE, "Could not save the state of " + player.getName()
                    + ", admin mode NOT enabled: " + e.getMessage(), e);
            messages().send(player, "mode-save-failed");
            return;
        }
        states.put(player.getUniqueId(), state);

        player.closeInventory();
        player.getInventory().clear();
        player.setItemOnCursor(null);
        for (PotionEffect effect : List.copyOf(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        player.setGameMode(settings().gameMode());
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20 : maxHealth.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0);
        player.setLevel(0);
        player.setExp(0f);
        ensureCompass(player);
        player.getInventory().setHeldItemSlot(settings().compassSlot());

        messages().send(player, "mode-on");
        Location l = player.getLocation();
        plugin.discord().log("admin-mode-on", "player", player.getName(), "world", l.getWorld().getName(),
                "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ());
        plugin.getLogger().info(player.getName() + " entered admin mode.");
    }

    public void disable(Player player, boolean quitting) {
        SavedState state = states.get(player.getUniqueId());
        if (state == null) {
            if (!quitting) {
                messages().send(player, "mode-already-off");
            }
            return;
        }
        player.closeInventory();
        state.apply(player);
        boolean teleported = true;
        if (settings().returnToStart()) {
            Location back = state.location();
            if (back != null) {
                player.teleport(back, PlayerTeleportEvent.TeleportCause.PLUGIN);
            } else {
                teleported = false;
            }
        }
        // Apply once more after the teleport: changing worlds can reset flight and some effects.
        state.apply(player);
        states.remove(player.getUniqueId());
        File file = file(player.getUniqueId());
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete " + file.getName() + " - delete it by hand.");
        }

        String duration = Texts.duration(Duration.between(state.since(), Instant.now()).toMillis());
        if (!quitting) {
            messages().send(player, teleported ? "mode-off" : "mode-off-no-world", tag("world", state.world()),
                    tag("duration", duration));
        }
        plugin.discord().log("admin-mode-off", "player", player.getName(), "duration", duration);
        plugin.getLogger().info(player.getName() + " left admin mode (" + duration + ").");
    }

    // --------------------------------------------------------------- join/quit

    /** Picks up a saved state when the player joins (still in admin mode after a quit, crash or restart). */
    public void resume(Player player, boolean joined) {
        File file = file(player.getUniqueId());
        if (!file.exists()) {
            return;
        }
        try {
            SavedState state = SavedState.load(file);
            states.put(player.getUniqueId(), state);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Admin mode file of " + player.getName() + " (" + file.getName()
                    + ") is unreadable - NOT deleted, please check it: " + e.getMessage());
            messages().send(player, "mode-load-failed");
            return;
        }
        if (joined && settings().endOnQuit()) {
            // Should already have ended on quit; the server probably crashed. Restore now.
            disable(player, false);
            return;
        }
        if (player.getGameMode() != settings().gameMode()) {
            player.setGameMode(settings().gameMode());
        }
        ensureCompass(player);
        if (!joined) {
            return;
        }
        messages().send(player, "mode-resumed", tag("duration",
                Texts.duration(Duration.between(states.get(player.getUniqueId()).since(), Instant.now()).toMillis())));
    }

    public void quit(Player player) {
        if (active(player) && settings().endOnQuit()) {
            disable(player, true);
        } else {
            states.remove(player.getUniqueId());
        }
    }

    // ------------------------------------------------------------------ compass

    public boolean isCompass(ItemStack item) {
        return item != null && !item.isEmpty() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.BYTE);
    }

    private ItemStack compass() {
        ItemStack item = new ItemStack(settings().compass());
        item.editMeta(meta -> {
            meta.displayName(messages().item("mode-compass-name"));
            meta.lore(messages().lore("mode-compass-lore"));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    /** Gives the compass back if it is gone (thrown away in the creative inventory ...). */
    void ensureCompass(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isCompass(item)) {
                return;
            }
        }
        if (isCompass(player.getItemOnCursor())) {
            return;
        }
        int slot = settings().compassSlot();
        ItemStack existing = player.getInventory().getItem(slot);
        if (existing != null && !existing.isEmpty()) {
            int free = player.getInventory().firstEmpty();
            if (free < 0) {
                return;
            }
            slot = free;
        }
        player.getInventory().setItem(slot, compass());
    }
}

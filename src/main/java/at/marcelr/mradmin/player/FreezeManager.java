package at.marcelr.mradmin.player;

import at.marcelr.mradmin.MRAdmin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import static at.marcelr.mradmin.Messages.tag;

/**
 * Frozen players (e.g. during a check for cheats): they cannot move, build, interact or fight until a staff member
 * unfreezes them. Stored in {@code frozen.yml}, so leaving and rejoining does not help.
 */
public final class FreezeManager {

    public static final String PERM_FREEZE = "mradmin.player.freeze";

    private final MRAdmin plugin;
    private final File file;
    private final Set<UUID> frozen = new HashSet<>();
    private BukkitTask reminder;

    public FreezeManager(MRAdmin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "frozen.yml");
    }

    public void load() {
        frozen.clear();
        if (file.exists()) {
            for (String id : YamlConfiguration.loadConfiguration(file).getStringList("frozen")) {
                try {
                    frozen.add(UUID.fromString(id));
                } catch (IllegalArgumentException ignored) {
                    // skip broken entries
                }
            }
        }
    }

    public void start() {
        stop();
        if (!plugin.feature(at.marcelr.mradmin.Feature.FREEZE)) {
            // Switched off: everybody is free - for good, nobody is frozen again when it is switched back on.
            if (!frozen.isEmpty()) {
                plugin.getLogger().info("Freeze is switched off (features.freeze) - " + frozen.size() + " frozen player(s) released.");
                frozen.clear();
                save();
            }
            return;
        }
        // Remind frozen players every few seconds - otherwise they think the server lags.
        reminder = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID id : frozen) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.sendActionBar(plugin.messages().get("freeze-actionbar"));
                }
            }
        }, 40L, 40L);
    }

    public void stop() {
        if (reminder != null) {
            reminder.cancel();
            reminder = null;
        }
    }

    public boolean frozen(UUID id) {
        return frozen.contains(id);
    }

    /** Freezes or unfreezes; tells the target, logs to the console and the admin Discord. */
    public void set(Player staff, Player target, boolean freeze) {
        if (freeze == frozen(target.getUniqueId())) {
            return;
        }
        if (freeze) {
            frozen.add(target.getUniqueId());
            target.setVelocity(new org.bukkit.util.Vector());
            plugin.messages().send(target, "freeze-frozen", tag("staff", staff.getName()));
        } else {
            frozen.remove(target.getUniqueId());
            plugin.messages().send(target, "freeze-unfrozen");
        }
        save();
        plugin.messages().send(staff, freeze ? "freeze-done" : "unfreeze-done", tag("player", target.getName()));
        plugin.getLogger().info(staff.getName() + (freeze ? " froze " : " unfroze ") + target.getName() + ".");
        plugin.discord().log(freeze ? "player-freeze" : "player-unfreeze", "admin", staff.getName(), "player", target.getName());
    }

    /** A frozen player left the server - staff should know (logging out to escape a check). */
    void quit(Player player) {
        if (!frozen(player.getUniqueId())) {
            return;
        }
        plugin.messages().broadcastTo(PERM_FREEZE, "freeze-quit", tag("player", player.getName()));
        plugin.discord().log("player-freeze-quit", "player", player.getName());
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("MRadmin - frozen players (managed by the plugin)."));
        y.set("frozen", new HashSet<>(frozen).stream().map(UUID::toString).toList());
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save frozen.yml: " + e.getMessage());
        }
    }
}

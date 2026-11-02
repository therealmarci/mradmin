package at.marcelr.mradmin.web.self;

import at.marcelr.mradmin.MRAdmin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The highest level every player ever had, for the statistics: the game itself only knows the current one, and that
 * falls with every death. So a level change is noted the moment it happens - the old level counts too, a death already
 * took it when anyone could read it. Written at most once a minute.
 */
public final class XpPeaks implements Listener {

    private static final String STATE = "xp-peaks";
    private static final long SAVE_TICKS = 60 * 20L;

    private final MRAdmin plugin;
    private final Map<UUID, Integer> peaks = new ConcurrentHashMap<>();
    private volatile boolean changed;
    private BukkitTask task;

    public XpPeaks(MRAdmin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        String saved = plugin.storage().loadState(STATE);
        if (saved != null) {
            YamlConfiguration data = new YamlConfiguration();
            try {
                data.loadFromString(saved);
                for (String key : data.getKeys(false)) {
                    peaks.put(UUID.fromString(key), data.getInt(key));
                }
            } catch (InvalidConfigurationException | IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Could not read the highest levels: " + e.getMessage());
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::save, SAVE_TICKS, SAVE_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        save();
    }

    /** Notes a level of the player. @return their highest one */
    public int seen(UUID player, int level) {
        int before = peaks.getOrDefault(player, 0);
        if (level <= before) {
            return before;
        }
        peaks.put(player, level);
        changed = true;
        return level;
    }

    private void save() {
        if (!changed) {
            return;
        }
        changed = false;
        YamlConfiguration data = new YamlConfiguration();
        peaks.forEach((player, level) -> data.set(player.toString(), level));
        plugin.storage().saveState(STATE, data.saveToString());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLevel(PlayerLevelChangeEvent event) {
        seen(event.getPlayer().getUniqueId(), Math.max(event.getOldLevel(), event.getNewLevel()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        seen(event.getPlayer().getUniqueId(), event.getPlayer().getLevel());
    }
}

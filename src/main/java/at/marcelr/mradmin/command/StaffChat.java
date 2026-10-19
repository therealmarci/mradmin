package at.marcelr.mradmin.command;

import at.marcelr.mradmin.MRAdmin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static at.marcelr.mradmin.Messages.tag;

/**
 * /sc &lt;message&gt; sends to all staff, /sc alone toggles: while on, normal chat goes to the staff chat.
 * Also usable from the console. Optionally mirrored to the admin Discord (event staff-chat).
 */
public final class StaffChat implements TabExecutor, Listener {

    public static final String PERM = at.marcelr.mradmin.Perms.STAFFCHAT;

    private final MRAdmin plugin;
    private final Set<UUID> toggled = ConcurrentHashMap.newKeySet();

    public StaffChat(MRAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return plugin.messages().as(sender, () -> {
            if (!plugin.feature(sender, at.marcelr.mradmin.Feature.STAFF_CHAT)) {
                return true;
            }
            if (!sender.hasPermission(PERM)) {
                plugin.messages().send(sender, "no-permission");
            } else if (args.length == 0) {
                if (sender instanceof Player player) {
                    toggle(player);
                } else {
                    plugin.messages().send(sender, "staffchat-usage");
                }
            } else {
                send(sender.getName(), String.join(" ", args));
            }
            return true;
        });
    }

    /** Whether the player's normal chat currently goes to the staff chat. */
    public boolean toggled(Player player) {
        return plugin.feature(at.marcelr.mradmin.Feature.STAFF_CHAT) && toggled.contains(player.getUniqueId());
    }

    /** Switches the player's chat between normal and staff chat and tells them. @return the new state */
    public boolean toggle(Player player) {
        boolean on = !toggled.remove(player.getUniqueId());
        if (on) {
            toggled.add(player.getUniqueId());
        }
        plugin.messages().send(player, on ? "staffchat-on" : "staffchat-off");
        return on;
    }

    /** Sends a message to every staff member and the console. */
    public void send(String from, String message) {
        Component line = plugin.messages().forPlayers(() ->
                plugin.messages().get("staffchat-format", tag("player", from), tag("message", message)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(PERM)) {
                player.sendMessage(line);
            }
        }
        plugin.getLogger().info("[Staff] " + from + ": " + message);
        plugin.discord().log("staff-chat", "player", from, "text", message);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!toggled(player)) {
            return;
        }
        if (!player.hasPermission(PERM)) {
            toggled.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        // Chat runs async; the rest of the plugin expects the main thread.
        Bukkit.getScheduler().runTask(plugin, () -> send(player.getName(), text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        toggled.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}

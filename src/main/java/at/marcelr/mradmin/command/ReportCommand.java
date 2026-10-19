package at.marcelr.mradmin.command;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.gui.TicketDialog;
import at.marcelr.mradmin.gui.TicketMenu;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.ticket.TicketManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static at.marcelr.mradmin.Messages.tag;

/**
 * /report - for every player:
 * <ul>
 *   <li>{@code /report} - the ticket menu (new ticket, own tickets)</li>
 *   <li>{@code /report <player> [text]} - report that player (choose a player category, then the form)</li>
 *   <li>{@code /report #<id>} - opens a ticket (used by the clickable notices; creator and team)</li>
 * </ul>
 */
public final class ReportCommand implements TabExecutor {

    private final MRAdmin plugin;

    public ReportCommand(MRAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return plugin.messages().as(sender, () -> {
            if (!plugin.feature(sender, at.marcelr.mradmin.Feature.TICKETS)) {
                return true;
            }
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "players-only");
            } else if (args.length > 0 && args[0].startsWith("#")) {
                view(player, args[0].substring(1));
            } else if (!sender.hasPermission(TicketManager.PERM_CREATE)) {
                plugin.messages().send(sender, "no-permission");
            } else if (args.length == 0) {
                new TicketMenu(plugin, player).open();
            } else {
                report(player, args);
            }
            return true;
        });
    }

    private void view(Player player, String id) {
        Ticket ticket;
        try {
            ticket = plugin.tickets().get(Integer.parseInt(id));
        } catch (NumberFormatException e) {
            ticket = null;
        }
        if (ticket == null || !plugin.tickets().canView(player, ticket)) {
            plugin.messages().send(player, "ticket-not-found", tag("id", id));
        } else {
            TicketDialog.view(plugin, player, ticket, null);
        }
    }

    private void report(Player player, String[] args) {
        OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[0]);
        }
        if (target == null) {
            plugin.messages().send(player, "player-not-found", tag("player", args[0]));
        } else if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.messages().send(player, "ticket-self");
        } else {
            String text = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
            TicketDialog.start(plugin, player, target, text, null);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !plugin.feature(at.marcelr.mradmin.Feature.TICKETS)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(sender) && (!(sender instanceof Player viewer) || viewer.canSee(p)))
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }
}

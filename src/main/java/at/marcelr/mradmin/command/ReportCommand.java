package at.marcelr.mradmin.command;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.report.ReportManager;
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

/** /report &lt;player&gt; &lt;reason...&gt; - for every player. */
public final class ReportCommand implements TabExecutor {

    private final MRAdmin plugin;

    public ReportCommand(MRAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return plugin.messages().as(sender, () -> {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "players-only");
            } else if (!sender.hasPermission(ReportManager.PERM_REPORT)) {
                plugin.messages().send(sender, "no-permission");
            } else if (args.length < 2) {
                plugin.messages().send(sender, "report-usage");
            } else {
                OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    target = Bukkit.getOfflinePlayerIfCached(args[0]);
                }
                if (target == null) {
                    plugin.messages().send(sender, "player-not-found", tag("player", args[0]));
                } else {
                    plugin.reports().create(player, target, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                }
            }
            return true;
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
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

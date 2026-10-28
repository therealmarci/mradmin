package at.marcelr.mradmin.command;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Perms;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

import static at.marcelr.mradmin.Messages.tag;

/**
 * /web - the player's own page in the web interface (player area, see
 * {@link at.marcelr.mradmin.web.self.SelfRoutes}):
 * <ul>
 *   <li>{@code /web} - a link that signs this browser in (works once, a few minutes); the page can be bookmarked</li>
 *   <li>{@code /web devices} - how many browsers are signed in, with a button to sign them all out</li>
 *   <li>{@code /web logout} - signs out every browser</li>
 * </ul>
 */
public final class SelfWebCommand implements TabExecutor {

    private final MRAdmin plugin;

    public SelfWebCommand(MRAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return plugin.messages().as(sender, () -> {
            var messages = plugin.messages();
            if (!(sender instanceof Player player)) {
                messages.send(sender, "players-only");
                return true;
            }
            if (!sender.hasPermission(Perms.WEB_SELF)) {
                messages.send(sender, "no-permission");
                return true;
            }
            var web = plugin.web();
            if (!plugin.settings().playerWeb().enabled() || web == null || !web.running()) {
                messages.send(sender, "web-self-disabled");
                return true;
            }
            String url = plugin.settings().web().publicUrl();
            if (url.isEmpty()) {
                messages.send(sender, "web-self-no-url");
                return true;
            }
            String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "devices", "geraete" -> {
                    int count = web.playerSessions().devices(player.getUniqueId()).size();
                    var button = messages.get("web-self-logout-button").clickEvent(ClickEvent.runCommand("/web logout"));
                    messages.send(sender, "web-self-devices", tag("count", count), tag("button", button));
                }
                case "logout", "abmelden" -> {
                    int count = web.playerSessions().revokeAll(player.getUniqueId());
                    messages.send(sender, "web-self-logout", tag("count", count));
                    plugin.getLogger().info(player.getName() + " signed out all browsers of their own page (" + count + ").");
                }
                default -> {
                    String code = web.playerSessions().code(player.getUniqueId(), player.getName());
                    // The code is in the fragment: browsers never send it to a server, it stays out of every log.
                    String link = url + "/me/login#" + code;
                    var button = messages.get("web-self-link-label").clickEvent(ClickEvent.openUrl(link));
                    messages.send(sender, "web-self-link", tag("link", button),
                            tag("minutes", plugin.settings().playerWeb().linkMinutes()));
                }
            }
            return true;
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? List.of("devices", "logout").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of();
    }
}

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
 *   <li>{@code /web} - the link to the page (always the same, can be bookmarked) and a 6-digit code to sign in there
 *   (works once, player-web.code-seconds; a click copies it)</li>
 *   <li>{@code /web status} - whether the web interface runs, the own sign-ins (password, passkeys, Discord, 2FA)
 *   and devices</li>
 *   <li>{@code /web devices} - how many browsers are signed in, with a button to sign them all out</li>
 *   <li>{@code /web logout} - signs out every browser</li>
 *   <li>{@code /web reset} - removes the own sign-ins (password, 2FA, passkeys, Discord, e-mail) and signs out every
 *   browser, after a click to confirm - e.g. a lost phone; afterwards only /web gets one in</li>
 * </ul>
 * Only the own account: nobody (also not the team) manages other players' accounts from the game.
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
            // The one way into the web interface without an own sign-in: players with the player page, and the team.
            boolean team = sender.hasPermission(at.marcelr.mradmin.web.auth.WebPermissions.PERM_WEB);
            if (!team && !sender.hasPermission(Perms.WEB_SELF)) {
                messages.send(sender, "no-permission");
                return true;
            }
            var web = plugin.web();
            if ((!team && !plugin.settings().playerWeb().enabled()) || web == null || !web.running()) {
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
                case "status" -> {
                    var account = web.auth().account(player.getUniqueId());
                    java.util.List<String> logins = new java.util.ArrayList<>();
                    if (account != null && account.hasPassword()) {
                        logins.add(messages.plain("web-self-login-password"));
                    }
                    if (account != null && account.passkeyCount() > 0) {
                        logins.add(messages.plain("web-self-login-passkeys", "count", account.passkeyCount()));
                    }
                    if (account != null && account.hasDiscord()) {
                        logins.add(messages.plain("web-self-login-discord"));
                    }
                    if (account != null && account.hasTotp()) {
                        logins.add(messages.plain("web-self-login-totp"));
                    }
                    messages.send(sender, "web-self-status", tag("address", url),
                            tag("logins", logins.isEmpty() ? messages.plain("web-self-login-none") : String.join(", ", logins)),
                            tag("count", web.playerSessions().devices(player.getUniqueId()).size()));
                }
                case "reset", "zuruecksetzen" -> {
                    boolean sure = args.length > 1 && args[1].equalsIgnoreCase("confirm");
                    if (!sure) {
                        var button = messages.get("web-self-reset-button").clickEvent(ClickEvent.runCommand("/web reset confirm"));
                        messages.send(sender, "web-self-reset-ask", tag("button", button));
                    } else if (web.auth().resetLogins(player.getUniqueId())) {
                        web.playerSessions().revokeAll(player.getUniqueId());
                        messages.send(sender, "web-self-reset-done");
                        plugin.getLogger().info(player.getName() + " removed all own sign-ins of the web interface.");
                        plugin.storage().audit(new at.marcelr.mradmin.storage.Storage.AuditRow(System.currentTimeMillis(),
                                player.getUniqueId(), player.getName(), "in game", "logins-reset", "by themselves (/web reset)"));
                    } else {
                        messages.send(sender, "web-self-reset-none");
                    }
                }
                case "logout", "abmelden" -> {
                    int count = web.playerSessions().revokeAll(player.getUniqueId());
                    messages.send(sender, "web-self-logout", tag("count", count));
                    plugin.getLogger().info(player.getName() + " signed out all browsers of their own page (" + count + ").");
                }
                default -> {
                    // Always the same address (bookmark); the page asks for the code, a click in the chat copies it.
                    String code = web.playerSessions().code(player.getUniqueId(), player.getName());
                    var link = messages.get("web-self-link-label").clickEvent(ClickEvent.openUrl(url + "/"));
                    var copy = messages.get("web-self-code", tag("code", code))
                            .clickEvent(ClickEvent.copyToClipboard(code))
                            .hoverEvent(messages.get("web-self-code-hover"));
                    messages.send(sender, "web-self-link", tag("link", link), tag("code", copy),
                            tag("seconds", plugin.settings().playerWeb().codeSeconds()));
                }
            }
            return true;
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 ? List.of("status", "devices", "logout", "reset").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of();
    }
}

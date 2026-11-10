package at.marcelr.mradmin.command;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.Texts;
import at.marcelr.mradmin.player.Npcs;
import at.marcelr.mradmin.storage.Storage;
import at.marcelr.mradmin.web.AuthRoutes;
import at.marcelr.mradmin.web.auth.WebAuth;
import at.marcelr.mradmin.web.auth.WebPermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static at.marcelr.mradmin.Messages.tag;

/**
 * /mradmin web ... - the in-game side of the web interface. Setup links are the only way to an account: a player
 * makes one for themselves ({@code setup}, needs mradmin.web), admins or the console for others ({@code invite}).
 * <pre>
 *  /mradmin web                      status
 *  /mradmin web setup                hint: own sign-ins are set up in the web (Account -> Sign-ins) since 1.5
 *  /mradmin web reset &lt;player&gt;       remove all own sign-ins (lost device)  mradmin.web.manage / console
 *  /mradmin web sessions [player]    signed-in browsers                       others: mradmin.web.manage
 *  /mradmin web logout [player]      sign out all browsers                    others: mradmin.web.manage
 *  /mradmin web remove &lt;player&gt;      delete the web account                  mradmin.web.manage
 * </pre>
 */
final class WebCommand {

    private final MRAdmin plugin;

    WebCommand(MRAdmin plugin) {
        this.plugin = plugin;
    }

    private Messages messages() {
        return plugin.messages();
    }

    private WebAuth auth() {
        return plugin.web().auth();
    }

    void handle(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "setup", "einrichten", "invite", "einladen" -> messages().send(sender, "web-setup-moved");
            case "reset", "zuruecksetzen" -> reset(sender, args);
            case "sessions", "geraete" -> sessions(sender, args);
            case "logout", "abmelden" -> logout(sender, args);
            case "remove", "delete", "loeschen" -> remove(sender, args);
            default -> status(sender);
        }
    }

    static List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return sender.hasPermission(AuthRoutes.PERM_MANAGE)
                    ? List.of("sessions", "logout", "reset", "remove") : List.of("sessions", "logout");
        }
        if (args.length == 3 && sender.hasPermission(AuthRoutes.PERM_MANAGE)) {
            return Npcs.online().stream().map(Player::getName).toList();
        }
        return List.of();
    }

    // ------------------------------------------------------------------ status

    private void status(CommandSender sender) {
        if (!check(sender, WebPermissions.PERM_WEB)) {
            return;
        }
        Settings.WebSettings s = plugin.settings().web();
        boolean running = plugin.web().running();
        messages().send(sender, "web-status",
                tag("state", messages().get(running ? "web-state-on" : "web-state-off")),
                tag("address", s.publicUrl().isEmpty() ? s.bind() + ":" + s.port() : s.publicUrl()),
                tag("accounts", auth().ownLoginCount()), tag("sessions", auth().sessionCount()));
        messages().send(sender, "web-usage");
    }

    // ------------------------------------------------------------------- reset

    /** Removes all own sign-ins of a player (lost device, taken-over account) - afterwards only /web gets them in. */
    private void reset(CommandSender sender, String[] args) {
        if (!check(sender, AuthRoutes.PERM_MANAGE)) {
            return;
        }
        OfflinePlayer target = target(sender, args);
        if (target == null) {
            return;
        }
        String name = target.getName() == null ? args[2] : target.getName();
        if (!auth().resetLogins(target.getUniqueId())) {
            messages().send(sender, "web-reset-none", tag("player", name));
            return;
        }
        messages().send(sender, "web-reset-done", tag("player", name));
        audit(sender, name, "logins-reset", "by " + sender.getName());
    }

    // ---------------------------------------------------------------- sessions

    private void sessions(CommandSender sender, String[] args) {
        OfflinePlayer target = selfOrOther(sender, args);
        if (target == null) {
            return;
        }
        String name = target.getName() == null ? "?" : target.getName();
        List<at.marcelr.mradmin.web.self.PlayerSessions.Session> list = auth().sessions(target.getUniqueId());
        if (list.isEmpty()) {
            messages().send(sender, "web-sessions-none", tag("player", name));
            return;
        }
        messages().send(sender, "web-sessions-header", tag("player", name));
        for (at.marcelr.mradmin.web.self.PlayerSessions.Session s : list) {
            messages().send(sender, "web-sessions-entry", tag("device", device(s.agent())), tag("ip", s.ip()),
                    tag("seen", Texts.at(plugin, Instant.ofEpochMilli(s.lastSeen()))));
        }
    }

    private void logout(CommandSender sender, String[] args) {
        OfflinePlayer target = selfOrOther(sender, args);
        if (target == null) {
            return;
        }
        String name = target.getName() == null ? "?" : target.getName();
        int count = auth().revokeAll(target.getUniqueId(), null);
        messages().send(sender, "web-logout-done", tag("player", name), tag("count", count));
        audit(sender, name, "sessions-revoked", count + " by " + sender.getName());
    }

    private void remove(CommandSender sender, String[] args) {
        if (!check(sender, AuthRoutes.PERM_MANAGE)) {
            return;
        }
        OfflinePlayer target = target(sender, args);
        if (target == null) {
            return;
        }
        String name = target.getName() == null ? args[2] : target.getName();
        if (auth().remove(target.getUniqueId())) {
            messages().send(sender, "web-remove-done", tag("player", name));
            audit(sender, name, "account-removed", "by " + sender.getName());
        } else {
            messages().send(sender, "web-no-account", tag("player", name));
        }
    }

    // ----------------------------------------------------------------- helpers

    /** The player named in args[2]: online, or known to the server. Tells the sender if not found. */
    private OfflinePlayer target(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages().send(sender, "web-usage");
            return null;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[2]);
        }
        if (target == null) {
            messages().send(sender, "player-not-found", tag("player", args[2]));
        }
        return target;
    }

    /** Without a name: the sender (a player); with one: someone else (mradmin.web.manage or console). */
    private OfflinePlayer selfOrOther(CommandSender sender, String[] args) {
        if (args.length >= 3) {
            return check(sender, AuthRoutes.PERM_MANAGE) ? target(sender, args) : null;
        }
        if (sender instanceof Player player) {
            return check(sender, WebPermissions.PERM_WEB) ? player : null;
        }
        messages().send(sender, "web-usage");
        return null;
    }

    private boolean running(CommandSender sender) {
        if (plugin.web().running()) {
            return true;
        }
        messages().send(sender, "web-off");
        return false;
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        messages().send(sender, "no-permission");
        return false;
    }

    private void audit(CommandSender sender, String name, String action, String detail) {
        java.util.UUID id = sender instanceof Player p ? p.getUniqueId() : null;
        plugin.storage().audit(new Storage.AuditRow(System.currentTimeMillis(), id, name,
                sender instanceof Player ? "in game" : "console", action, detail));
    }

    private static String device(String agent) {
        return at.marcelr.mradmin.web.AuthRoutes.device(agent);
    }
}

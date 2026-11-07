package at.marcelr.mradmin.command;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Perms;
import at.marcelr.mradmin.Texts;
import at.marcelr.mradmin.starter.Paper;
import at.marcelr.mradmin.util.DurationUtil;
import at.marcelr.mradmin.versions.RestartManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

/**
 * {@code /mradmin restart|stop [time|now|cancel]} - restart or stop with the countdown of the web interface, and
 * {@code /mradmin starter [setup [mode]]} - status of the starter (mrserver), set it up in the server folder.
 */
final class ServerCommand {

    private final MRAdmin plugin;

    ServerCommand(MRAdmin plugin) {
        this.plugin = plugin;
    }

    private Messages messages() {
        return plugin.messages();
    }

    void handle(CommandSender sender, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("starter")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("setup")) {
                if (sender.hasPermission(Perms.SERVER_SETUP)) {
                    setup(sender, args.length >= 3 ? args[2] : null);
                } else {
                    messages().send(sender, "no-permission");
                }
            } else if (sender.hasPermission(Perms.SERVER_RESTART)) {
                status(sender);
            } else {
                messages().send(sender, "no-permission");
            }
            return;
        }
        if (!sender.hasPermission(Perms.SERVER_RESTART)) {
            messages().send(sender, "no-permission");
            return;
        }
        boolean restart = sub.equals("restart") || sub.equals("neustart");
        String cmd = restart ? "restart" : "stop";
        RestartManager restarts = plugin.restarts();
        String time = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (time.equals("cancel") || time.equals("abbrechen")) {
            if (!restarts.cancel(sender.getName())) {
                messages().send(sender, "server-countdown-none");
            }
            return;
        }
        if (restart && !restarts.enabled()) {
            messages().send(sender, "server-restart-off");
            return;
        }
        // [time] [reason ...] - without a time the configured countdown applies and everything is the reason.
        int from = 2;
        if (!time.isEmpty() && !time.matches("now|jetzt|[0-9]+|([0-9]+[smhdw])+")) {
            time = "";
            from = 1;
        }
        String reason = args.length > from ? String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length)) : null;
        long delay = time.isEmpty() ? plugin.starter().settings().countdown()
                : time.equals("now") || time.equals("jetzt") ? 0 : DurationUtil.parse(time);
        if (delay < 0 || delay > 24 * 3_600_000L) {
            messages().send(sender, "server-invalid-time", unparsed("time", time));
            return;
        }
        if (restarts.running()) {
            messages().send(sender, "server-countdown-busy");
            return;
        }
        if (restarts.start(delay, sender.getName(), restart, reason)) {
            messages().send(sender, "server-countdown-started",
                    unparsed("what", messages().plain(restart ? "server-what-restart" : "server-what-stop")),
                    unparsed("time", delay == 0 ? "0s" : Texts.duration(delay)), unparsed("cmd", cmd));
        }
    }

    private void status(CommandSender sender) {
        if (plugin.starter().active()) {
            String mode = System.getenv("MRADMIN_STARTER_MODE");
            messages().send(sender, "server-status-on", unparsed("mode", mode == null ? "?" : mode),
                    unparsed("build", String.valueOf(Paper.buildOf(Bukkit.getVersion()))));
        } else {
            messages().send(sender, "server-status-off",
                    unparsed("method", plugin.settings().versions().restartMethod()));
        }
        RestartManager restarts = plugin.restarts();
        if (restarts.running()) {
            messages().send(sender, "server-status-countdown",
                    unparsed("what", messages().plain(restarts.restarting() ? "server-what-restart" : "server-what-stop")),
                    unparsed("time", Texts.duration(Math.max(0, restarts.endsAt() - System.currentTimeMillis()))),
                    unparsed("by", restarts.by()));
        }
        ZonedDateTime next = plugin.starter().nextPlanned();
        if (next != null && restarts.enabled()) {
            messages().send(sender, "server-status-next", unparsed("time", Texts.at(plugin, next.toInstant())));
        }
        Paper.Build update = plugin.starter().paperUpdate();
        if (update != null) {
            messages().send(sender, "server-status-paper", unparsed("new", String.valueOf(update.id())),
                    unparsed("old", String.valueOf(Paper.buildOf(Bukkit.getVersion()))),
                    unparsed("mode", plugin.starter().settings().paperUpdate()));
        }
    }

    private void setup(CommandSender sender, String mode) {
        if (mode != null && !mode.toLowerCase(Locale.ROOT).matches("screen|systemd|window|foreground")) {
            messages().send(sender, "server-setup-mode", unparsed("mode", mode));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var result = plugin.starter().setup(mode);
                plugin.getLogger().info(sender.getName() + " set up the starter (mode " + result.local().mode() + ").");
                messages().as(sender, () -> {
                    messages().send(sender, plugin.starter().active() ? "server-setup-refreshed" : "server-setup-done",
                            unparsed("mode", result.local().mode()));
                    return null;
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Starter setup failed: " + e.getMessage());
                messages().as(sender, () -> {
                    messages().send(sender, "server-setup-failed", unparsed("reason", String.valueOf(e.getMessage())));
                    return null;
                });
            }
        });
    }

    static List<String> complete(CommandSender sender, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length != 2 && !(sub.equals("starter") && args.length == 3)) {
            return List.of();
        }
        if (sub.equals("starter")) {
            if (args.length == 2) {
                return sender.hasPermission(Perms.SERVER_SETUP) ? List.of("status", "setup") : List.of("status");
            }
            return args[1].equalsIgnoreCase("setup") ? List.of("screen", "systemd", "window", "foreground") : List.of();
        }
        return sender.hasPermission(Perms.SERVER_RESTART) ? List.of("5m", "1m", "30s", "now", "cancel") : List.of();
    }
}

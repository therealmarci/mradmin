package at.marcelr.mradmin.command;

import at.marcelr.mradmin.DiscordWebhook;
import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Texts;
import at.marcelr.mradmin.UpdateChecker;
import at.marcelr.mradmin.announce.Announcer;
import at.marcelr.mradmin.discord.DiscordAnnouncer;
import at.marcelr.mradmin.gui.HubMenu;
import at.marcelr.mradmin.gui.PlayerActionMenu;
import at.marcelr.mradmin.gui.TicketDialog;
import at.marcelr.mradmin.gui.TicketsMenu;
import at.marcelr.mradmin.gui.ServerStatusMenu;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.ticket.TicketManager;
import at.marcelr.mradmin.maintenance.KumaClient;
import at.marcelr.mradmin.maintenance.MaintenanceManager;
import at.marcelr.mradmin.maintenance.Window;
import at.marcelr.mradmin.mode.AdminModeManager;
import at.marcelr.mradmin.mode.SavedState;
import at.marcelr.mradmin.util.DurationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static at.marcelr.mradmin.Messages.tag;

/** /mradmin - every sub command checks its own permission. Usable from the console (e.g. restart scripts). */
public final class MRAdminCommand implements TabExecutor {

    public static final String PERM_ANNOUNCE = "mradmin.announce";
    public static final String PERM_MODE_OTHERS = "mradmin.mode.others";
    public static final String PERM_RELOAD = "mradmin.admin.reload";
    public static final String PERM_DISCORD = "mradmin.admin.discord";

    private final MRAdmin plugin;
    private final WebCommand webCommand;

    public MRAdminCommand(MRAdmin plugin) {
        this.plugin = plugin;
        this.webCommand = new WebCommand(plugin);
    }

    private Messages messages() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // From the console every text is English, also values built along the way.
        return messages().as(sender, () -> handle(sender, args));
    }

    private boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0 && sender instanceof Player player && sender.hasPermission(AdminModeManager.PERM_USE)) {
            // Players get the dashboard; the command list stays available with /mradmin help.
            new HubMenu(plugin, player).open();
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "announce", "ankuendigung", "broadcast", "bc" -> {
                if (check(sender, PERM_ANNOUNCE) && plugin.feature(sender, Feature.ANNOUNCE)) {
                    announce(sender, args);
                }
            }
            case "mode", "modus", "adminmode" -> mode(sender, args);
            case "maintenance", "wartung", "maint" -> {
                if (check(sender, MaintenanceManager.PERM_MANAGE) && plugin.feature(sender, Feature.MAINTENANCE)) {
                    maintenance(sender, args);
                }
            }
            case "discord" -> {
                if (check(sender, PERM_DISCORD)) {
                    discord(sender, args);
                }
            }
            case "reload" -> {
                if (check(sender, PERM_RELOAD)) {
                    String[] problem = plugin.reload();
                    if (problem != null) {
                        messages().send(sender, "reload-invalid", tag("line", problem[1]), tag("error", problem[0]));
                    } else {
                        messages().send(sender, "reloaded");
                        plugin.discord().log("admin-reload", "admin", sender.getName());
                    }
                }
            }
            case "player", "spieler", "p" -> player(sender, args);
            case "status", "server", "tps" -> {
                if (plugin.feature(sender, Feature.SERVER_STATUS)) {
                    withPlayer(sender, ServerStatusMenu.PERM,
                            viewer -> new ServerStatusMenu(plugin, viewer, () -> new HubMenu(plugin, viewer).open()).open());
                }
            }
            case "tickets", "ticket", "reports", "report" -> {
                if (plugin.feature(sender, Feature.TICKETS)) {
                    tickets(sender, args);
                }
            }
            case "tp", "teleport" -> {
                if (plugin.feature(sender, Feature.TELEPORT)) {
                    teleport(sender, args);
                }
            }
            case "web" -> webCommand.handle(sender, args);
            case "storage", "speicher", "db" -> {
                if (check(sender, PERM_RELOAD)) {
                    storage(sender);
                }
            }
            case "version", "ver" -> version(sender);
            default -> help(sender);
        }
        return true;
    }

    /** Every line only appears if the sender may use that command. */
    private void help(CommandSender sender) {
        messages().send(sender, "help-header");
        if (sender.hasPermission(PERM_ANNOUNCE) && plugin.feature(Feature.ANNOUNCE)) {
            messages().send(sender, "help-announce", tag("presets", String.join(", ", plugin.announcer().presets())));
        }
        if (sender.hasPermission(AdminModeManager.PERM_USE) && plugin.feature(Feature.ADMIN_MODE)) {
            messages().send(sender, "help-mode");
        }
        if (sender.hasPermission(MaintenanceManager.PERM_MANAGE) && plugin.feature(Feature.MAINTENANCE)) {
            messages().send(sender, "help-maintenance");
        }
        if (sender.hasPermission(PERM_DISCORD)) {
            messages().send(sender, "help-discord");
        }
        if (sender.hasPermission(PERM_RELOAD)) {
            messages().send(sender, "help-reload");
        }
        if (sender.hasPermission(PERM_RELOAD)) {
            messages().send(sender, "help-storage");
        }
        if (sender.hasPermission(at.marcelr.mradmin.web.auth.WebPermissions.PERM_WEB)) {
            messages().send(sender, "help-web");
        }
        if (sender.hasPermission(AdminModeManager.PERM_USE)) {
            messages().send(sender, "help-menu");
        }
        if (sender.hasPermission(ServerStatusMenu.PERM) && plugin.feature(Feature.SERVER_STATUS)) {
            messages().send(sender, "help-status");
        }
        if (sender.hasPermission(TicketManager.PERM_MANAGE) && plugin.feature(Feature.TICKETS)) {
            messages().send(sender, "help-tickets");
        }
        if (sender.hasPermission(StaffChat.PERM) && plugin.feature(Feature.STAFF_CHAT)) {
            messages().send(sender, "help-staffchat");
        }
        messages().send(sender, "help-version");
    }

    // --------------------------------------------------------------- menus

    /** Runs {@code action} for a player sender with {@code permission}. */
    private void withPlayer(CommandSender sender, String permission, java.util.function.Consumer<Player> action) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "players-only");
        } else if (check(sender, permission)) {
            action.accept(player);
        }
    }

    /** /mradmin player <name> - action menu of an online player. */
    private void player(CommandSender sender, String[] args) {
        withPlayer(sender, AdminModeManager.PERM_USE, viewer -> {
            Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
            if (target == null) {
                messages().send(sender, "player-not-found", tag("player", args.length > 1 ? args[1] : "?"));
            } else {
                new PlayerActionMenu(plugin, viewer, target, () -> new HubMenu(plugin, viewer).open()).open();
            }
        });
    }

    /** /mradmin tp <player> */
    private void teleport(CommandSender sender, String[] args) {
        withPlayer(sender, AdminModeManager.PERM_TELEPORT, viewer -> {
            Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
            if (target == null) {
                messages().send(sender, "player-not-found", tag("player", args.length > 1 ? args[1] : "?"));
            } else {
                viewer.teleport(target.getLocation(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                messages().send(viewer, "mode-teleported", tag("player", target.getName()));
            }
        });
    }

    /** /mradmin tickets [view|tp|close <id>] - the team's ticket menu, or the actions of the clickable notices. */
    private void tickets(CommandSender sender, String[] args) {
        withPlayer(sender, TicketManager.PERM_MANAGE, viewer -> {
            if (args.length < 3) {
                new TicketsMenu(plugin, viewer, null, null).open();
                return;
            }
            Ticket ticket;
            try {
                ticket = plugin.tickets().get(Integer.parseInt(args[2].replace("#", "")));
            } catch (NumberFormatException e) {
                ticket = null;
            }
            if (ticket == null) {
                messages().send(sender, "ticket-not-found", tag("id", args[2]));
                return;
            }
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "close" -> plugin.tickets().close(viewer, ticket);
                case "tp", "teleport" -> plugin.tickets().teleport(viewer, ticket);
                default -> TicketDialog.view(plugin, viewer, ticket, null);
            }
        });
    }

    // -------------------------------------------------------------- announce

    /**
     * /mradmin announce [-d|-q] <preset|text...>. Presets with a discord text also go to the community Discord
     * (-q = not this time), own texts only with -d.
     */
    private void announce(CommandSender sender, String[] args) {
        int from = 1;
        boolean toDiscord = false;
        boolean quiet = false;
        while (from < args.length && (args[from].equalsIgnoreCase("-d") || args[from].equalsIgnoreCase("-q"))) {
            if (args[from].equalsIgnoreCase("-d")) {
                toDiscord = true;
            } else {
                quiet = true;
            }
            from++;
        }
        if (args.length <= from) {
            messages().send(sender, "announce-usage", tag("presets", String.join(", ", plugin.announcer().presets())));
            return;
        }
        Announcer announcer = plugin.announcer();
        var preset = announcer.preset(args[from]);
        if (preset.isPresent()) {
            announcer.announcePreset(preset.get(), List.of(Arrays.copyOfRange(args, from + 1, args.length)), sender.getName(), !quiet);
        } else {
            announcer.announceText(String.join(" ", Arrays.copyOfRange(args, from, args.length)), sender.getName(), toDiscord && !quiet);
        }
        if (!(sender instanceof Player)) {
            messages().send(sender, "announce-sent");
        }
    }

    // ------------------------------------------------------------------ mode

    /** /mradmin mode [on|off|toggle|list] [player] */
    private void mode(CommandSender sender, String[] args) {
        if (!check(sender, AdminModeManager.PERM_USE) || !plugin.feature(sender, Feature.ADMIN_MODE)) {
            return;
        }
        AdminModeManager mode = plugin.adminMode();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "toggle";
        if (action.equals("list")) {
            if (mode.all().isEmpty()) {
                messages().send(sender, "mode-list-empty");
                return;
            }
            messages().send(sender, "mode-list-header");
            for (SavedState state : mode.all()) {
                messages().send(sender, "mode-list-entry", tag("player", state.name()),
                        tag("duration", Texts.duration(Duration.between(state.since(), Instant.now()).toMillis())));
            }
            return;
        }
        Player target;
        if (args.length > 2) {
            if (!check(sender, PERM_MODE_OTHERS)) {
                return;
            }
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                messages().send(sender, "player-not-found", tag("player", args[2]));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            messages().send(sender, "mode-usage");
            return;
        }
        Boolean on = switch (action) {
            case "on", "an", "ein", "enable" -> true;
            case "off", "aus", "disable" -> false;
            case "toggle" -> !mode.active(target);
            default -> null;
        };
        if (on == null) {
            messages().send(sender, "mode-usage");
            return;
        }
        if (on) {
            mode.enable(target);
        } else {
            mode.disable(target, false);
        }
        if (target != sender) {
            messages().send(sender, on ? "mode-on-other" : "mode-off-other", tag("player", target.getName()));
        }
    }

    // ----------------------------------------------------------- maintenance

    /** /mradmin maintenance [status|on|schedule|extend|off|refresh] */
    private void maintenance(CommandSender sender, String[] args) {
        MaintenanceManager manager = plugin.maintenance();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "on", "start", "an" -> {
                // on [duration] [title...]
                int titleFrom = 2;
                Duration duration = null;
                if (args.length > 2) {
                    long ms = DurationUtil.parse(args[2]);
                    if (ms > 0) {
                        duration = Duration.ofMillis(ms);
                        titleFrom = 3;
                    }
                }
                manager.startNow(duration, join(args, titleFrom), sender.getName());
                messages().send(sender, "maintenance-cmd-on", tag("end", Texts.at(plugin, duration == null ? null : Instant.now().plus(duration))));
            }
            case "schedule", "plan", "planen" -> {
                // schedule <in> [duration] [title...]
                if (args.length < 3 || DurationUtil.parse(args[2]) <= 0) {
                    messages().send(sender, "maintenance-usage");
                    return;
                }
                Duration in = Duration.ofMillis(DurationUtil.parse(args[2]));
                Duration duration = null;
                int titleFrom = 3;
                if (args.length > 3 && DurationUtil.parse(args[3]) > 0) {
                    duration = Duration.ofMillis(DurationUtil.parse(args[3]));
                    titleFrom = 4;
                }
                manager.schedule(in, duration, join(args, titleFrom), sender.getName());
                messages().send(sender, "maintenance-cmd-scheduled", tag("start", Texts.at(plugin, Instant.now().plus(in))));
            }
            case "extend", "verlaengern" -> {
                long ms = args.length > 2 ? DurationUtil.parse(args[2].replace("-", "")) : -1;
                if (ms <= 0) {
                    messages().send(sender, "maintenance-usage");
                    return;
                }
                Duration by = Duration.ofMillis(args[2].startsWith("-") ? -ms : ms);
                if (manager.extendManual(by)) {
                    messages().send(sender, "maintenance-cmd-extended",
                            tag("end", Texts.at(plugin, manager.manual() == null ? null : manager.manual().end())));
                } else {
                    messages().send(sender, "maintenance-cmd-extend-failed");
                }
            }
            case "off", "stop", "aus", "cancel" -> {
                Window current = manager.current();
                if (manager.stopCurrent(sender.getName())) {
                    messages().send(sender, current != null && current.source().equals(Window.KUMA)
                            ? "maintenance-cmd-off-kuma" : "maintenance-cmd-off");
                } else {
                    messages().send(sender, "maintenance-cmd-none");
                }
            }
            case "refresh", "sync" -> {
                manager.refreshKuma();
                messages().send(sender, "maintenance-cmd-refresh");
            }
            case "status", "info" -> maintenanceStatus(sender);
            default -> messages().send(sender, "maintenance-usage");
        }
    }

    private void maintenanceStatus(CommandSender sender) {
        MaintenanceManager manager = plugin.maintenance();
        Window current = manager.current();
        var resolvers = manager.resolvers(current, Instant.now());
        messages().send(sender, "maintenance-status-header");
        String phase = switch (manager.phase()) {
            case NONE -> "maintenance-status-none";
            case PLANNED -> "maintenance-status-planned";
            case UPCOMING -> "maintenance-status-upcoming";
            case ACTIVE -> "maintenance-status-active";
        };
        messages().send(sender, phase, resolvers);
        if (current != null) {
            messages().send(sender, "maintenance-status-source", tag("source", current.source().equals(Window.KUMA)
                    ? "Uptime Kuma #" + current.id() : messages().plain("maintenance-source-manual")));
        }
        KumaClient kuma = manager.kuma();
        String state = switch (kuma.state()) {
            case DISABLED -> "kuma-state-disabled";
            case CONNECTING -> "kuma-state-connecting";
            case CONNECTED -> "kuma-state-connected";
            case LOGIN_FAILED -> "kuma-state-login-failed";
            case OFFLINE -> "kuma-state-offline";
        };
        String problem = kuma.problem();
        messages().send(sender, state,
                tag("count", kuma.lastCount()),
                tag("updated", kuma.lastUpdate() == null ? "-" : Texts.at(plugin, kuma.lastUpdate())),
                tag("reason", problem == null ? "" : problem));
    }

    private static String join(String[] args, int from) {
        return from >= args.length ? "" : String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    // ------------------------------------------------------------------ misc

    private void discord(CommandSender sender, String[] args) {
        DiscordWebhook discord = plugin.discord();
        if (discord.enabled()) {
            int[] counts = discord.eventCounts();
            messages().send(sender, "discord-status-active", tag("events", counts[0]), tag("total", counts[1]));
        } else {
            messages().send(sender, "discord-status-inactive", tag("reason", discord.disabledReason()));
        }
        String last = discord.lastResult();
        messages().send(sender, "discord-status-last", tag("result", last == null ? messages().plain("discord-status-never") : last));
        DiscordAnnouncer announce = plugin.discordAnnounce();
        if (announce.enabled()) {
            String tracked = announce.trackedKey();
            messages().send(sender, "discord-announce-status-active", tag("tracked", tracked == null ? "-" : tracked));
        } else {
            messages().send(sender, "discord-announce-status-inactive", tag("reason", announce.disabledReason()));
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("test")) {
            if (discord.enabled() || announce.enabled()) {
                messages().send(sender, "discord-test-hint");
            }
            return;
        }
        // /mradmin discord test [announce]
        boolean announceTest = args.length > 2 && args[2].equalsIgnoreCase("announce");
        if (announceTest ? !announce.enabled() : !discord.enabled()) {
            return;
        }
        messages().send(sender, "discord-test-sending");
        var test = announceTest ? announce.test(sender) : discord.test(sender);
        test.whenComplete((error, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            String problem = throwable != null ? throwable.getMessage() : error;
            messages().as(sender, () -> {
                if (problem == null) {
                    messages().send(sender, "discord-test-ok");
                } else {
                    messages().send(sender, "discord-test-failed", tag("reason", problem));
                }
                return null;
            });
        }));
    }

    /** /mradmin storage - where the data is kept, and whether a changed setting still waits for a restart. */
    private void storage(CommandSender sender) {
        var active = plugin.storage();
        messages().send(sender, "storage-status", tag("type", active.type()), tag("target", active.describe()));
        String configured = plugin.settings().storage().type();
        if (!configured.equals(active.type())) {
            messages().send(sender, "storage-pending", tag("type", configured));
        }
    }

    private void version(CommandSender sender) {
        UpdateChecker updates = plugin.updateChecker();
        messages().send(sender, "version",
                tag("version", updates.currentVersion()), tag("mc", updates.minecraftVersion()),
                tag("author", String.join(", ", plugin.getPluginMeta().getAuthors())));
        Runnable links = () -> messages().as(sender, () -> {
            messages().send(sender, "version-links",
                    tag("modrinth", link("Modrinth", updates.modrinthPage())),
                    tag("github", link("GitHub", updates.githubPage())));
            return null;
        });
        if (plugin.settings().update().enabled()) {
            updates.checkNow(sender, links);
        } else {
            updates.sendStatus(sender);
            links.run();
        }
    }

    private static Component link(String label, String url) {
        return Component.text(label).color(NamedTextColor.AQUA).decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url));
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        messages().send(sender, "no-permission");
        return false;
    }

    // ------------------------------------------------------------------ tab

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("version");
            addIf(sender, PERM_ANNOUNCE, Feature.ANNOUNCE, options, "announce");
            addIf(sender, AdminModeManager.PERM_USE, Feature.ADMIN_MODE, options, "mode");
            addIf(sender, MaintenanceManager.PERM_MANAGE, Feature.MAINTENANCE, options, "maintenance");
            addIf(sender, PERM_DISCORD, options, "discord");
            addIf(sender, PERM_RELOAD, options, "reload");
            addIf(sender, PERM_RELOAD, options, "storage");
            addIf(sender, at.marcelr.mradmin.web.auth.WebPermissions.PERM_WEB, options, "web");
            options.add("help");
            addIf(sender, AdminModeManager.PERM_USE, options, "player");
            addIf(sender, ServerStatusMenu.PERM, Feature.SERVER_STATUS, options, "status");
            addIf(sender, TicketManager.PERM_MANAGE, Feature.TICKETS, options, "tickets");
            addIf(sender, AdminModeManager.PERM_TELEPORT, Feature.TELEPORT, options, "tp");
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "announce", "ankuendigung", "broadcast", "bc" -> {
                    boolean afterFlags = java.util.stream.IntStream.range(1, args.length - 1)
                            .allMatch(i -> args[i].equalsIgnoreCase("-d") || args[i].equalsIgnoreCase("-q"));
                    if (afterFlags && sender.hasPermission(PERM_ANNOUNCE)) {
                        options.addAll(plugin.announcer().presets());
                        options.addAll(List.of("-d", "-q"));
                    }
                }
                case "mode", "modus", "adminmode" -> {
                    if (args.length == 2) {
                        options.addAll(List.of("on", "off", "list"));
                    } else if (args.length == 3 && sender.hasPermission(PERM_MODE_OTHERS)) {
                        Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                    }
                }
                case "maintenance", "wartung", "maint" -> {
                    if (!sender.hasPermission(MaintenanceManager.PERM_MANAGE)) {
                        break;
                    }
                    if (args.length == 2) {
                        options.addAll(List.of("status", "on", "schedule", "extend", "off", "refresh"));
                    } else if (args.length == 3) {
                        switch (args[1].toLowerCase(Locale.ROOT)) {
                            case "on" -> options.addAll(List.of("30m", "1h", "2h"));
                            case "schedule" -> options.addAll(List.of("15m", "30m", "1h"));
                            case "extend" -> options.addAll(List.of("15m", "30m", "1h", "-15m"));
                            default -> {
                            }
                        }
                    } else if (args.length == 4 && args[1].equalsIgnoreCase("schedule")) {
                        options.addAll(List.of("30m", "1h", "2h"));
                    }
                }
                case "player", "spieler", "p", "tp", "teleport" -> {
                    if (args.length == 2) {
                        Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                    }
                }
                case "web" -> options.addAll(WebCommand.complete(sender, args));
                case "discord" -> {
                    if (args.length == 2) {
                        options.add("test");
                    } else if (args.length == 3 && args[1].equalsIgnoreCase("test")) {
                        options.add("announce");
                    }
                }
                default -> {
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options.stream().distinct().toList();
    }

    private static void addIf(CommandSender sender, String permission, List<String> target, String... values) {
        if (sender.hasPermission(permission)) {
            target.addAll(List.of(values));
        }
    }

    private void addIf(CommandSender sender, String permission, Feature feature, List<String> target, String... values) {
        if (plugin.feature(feature)) {
            addIf(sender, permission, target, values);
        }
    }
}

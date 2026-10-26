package at.marcelr.mradmin.gui;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.Texts;
import at.marcelr.mradmin.hook.Integration;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.ticket.TicketManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static at.marcelr.mradmin.Messages.tag;

/**
 * The forms of the ticket system as native Minecraft dialogs: opening a ticket (player report, bug, other), entering
 * a player name, and the ticket itself with its conversation, a reply field and the actions.
 */
public final class TicketDialog {

    private static final String SUBJECT = "subject";
    private static final String TEXT = "text";
    private static final String NAME = "name";
    private static final int WIDTH = 320;
    /** Width of the text next to a head or icon (header and chat bubbles). */
    private static final int BUBBLE = 290;
    /** Messages by the same author at most this far apart are shown as one bubble. */
    private static final Duration GROUP_GAP = Duration.ofMinutes(5);
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(Duration.ofMinutes(30)).build();

    /** What the player had typed - shown again if the form has to be repeated. */
    private record Draft(String subject, String text) {
    }

    private TicketDialog() {
    }

    // ------------------------------------------------------------------ create

    /** Step 1 of a new ticket: a button for every category the player may choose. */
    public static void start(MRAdmin plugin, Player viewer, Runnable back) {
        start(plugin, viewer, null, null, back);
    }

    /**
     * Step 1 of a new ticket. With a known {@code target} (/report &lt;player&gt;) only player categories are offered.
     * A single possible category is chosen right away.
     *
     * @param text prefilled description, may be null
     * @param back where "Cancel" leads; null = it just closes
     */
    public static void start(MRAdmin plugin, Player viewer, OfflinePlayer target, String text, Runnable back) {
        Messages messages = plugin.messages();
        TicketManager tickets = plugin.tickets();
        if (!tickets.canStart(viewer)) {
            return;
        }
        List<Settings.TicketCategory> categories = tickets.categories(viewer).stream()
                .filter(c -> target == null || c.player())
                .toList();
        if (categories.isEmpty()) {
            messages.send(viewer, "ticket-no-category");
            return;
        }
        if (categories.size() == 1) {
            choose(plugin, viewer, categories.getFirst(), target, text, back);
            return;
        }
        Runnable again = () -> start(plugin, viewer, target, text, back);
        List<ActionButton> buttons = new ArrayList<>();
        for (Settings.TicketCategory category : categories) {
            Component label = messages.get(category.player() ? "ticket-start-option-player" : "ticket-start-option",
                    tag("category", tickets.categoryName(category.id())));
            buttons.add(ActionButton.create(label, null, 260,
                    action(plugin, viewer, response -> choose(plugin, viewer, category, target, text, again))));
        }
        ActionButton cancel = ActionButton.create(messages.get("ticket-form-cancel"), null, 150, backAction(plugin, viewer, back));
        List<DialogBody> body = new ArrayList<>();
        if (target != null) {
            body.add(targetBody(plugin, target));
        }
        body.add(DialogBody.plainMessage(messages.get(target == null ? "ticket-start-hint" : "ticket-start-hint-player"), WIDTH));
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("ticket-start-title"))
                        .canCloseWithEscape(true)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(buttons).columns(1).exitAction(cancel).build()));
        show(viewer, dialog);
    }

    /** After choosing a category: the player list for player reports, otherwise straight to the form. */
    private static void choose(MRAdmin plugin, Player viewer, Settings.TicketCategory category, OfflinePlayer target,
                               String text, Runnable back) {
        if (category.player() && target == null) {
            new TicketPlayerMenu(plugin, viewer, category, text, back).open();
        } else {
            create(plugin, viewer, category, target, text, back);
        }
    }

    /**
     * The form of a category: title (not for player reports) and description. Checks cooldown and limits first.
     *
     * @param target the reported player (player categories only)
     * @param text   prefilled description, may be null
     */
    public static void create(MRAdmin plugin, Player viewer, Settings.TicketCategory category, OfflinePlayer target,
                              String text, Runnable back) {
        if (!plugin.tickets().canStart(viewer)) {
            return;
        }
        form(plugin, viewer, category, target, new Draft("", text == null ? "" : text), back);
    }

    private static void form(MRAdmin plugin, Player viewer, Settings.TicketCategory category, OfflinePlayer target,
                             Draft draft, Runnable back) {
        Messages messages = plugin.messages();
        Settings.TicketSettings s = plugin.settings().tickets();
        TicketManager tickets = plugin.tickets();
        String categoryName = tickets.categoryName(category.id());
        List<DialogBody> body = new ArrayList<>();
        List<DialogInput> inputs = new ArrayList<>();
        if (category.player()) {
            body.add(targetBody(plugin, target));
            body.add(DialogBody.plainMessage(messages.get("ticket-form-player-hint"), WIDTH));
        } else {
            body.add(DialogBody.plainMessage(messages.get("ticket-form-other-hint"), WIDTH));
            inputs.add(DialogInput.text(SUBJECT, messages.get("ticket-form-subject"))
                    .width(WIDTH).maxLength(s.maxSubjectLength()).initial(cut(draft.subject(), s.maxSubjectLength())).build());
        }
        inputs.add(DialogInput.text(TEXT, messages.get("ticket-form-text"))
                .width(WIDTH).maxLength(s.maxTextLength()).initial(cut(draft.text(), s.maxTextLength()))
                .multiline(TextDialogInput.MultilineOptions.create(12, 110)).build());

        DialogAction send = action(plugin, viewer, response -> {
            Draft entered = new Draft(text(response.getText(SUBJECT)), text(response.getText(TEXT)));
            Ticket ticket = tickets.create(viewer, category, target, entered.subject(), entered.text());
            if (ticket == null) {
                // Invalid input (the player was told why): show the form again with what they typed.
                if (tickets.mayCreate(viewer)) {
                    form(plugin, viewer, category, target, entered, back);
                }
            } else {
                view(plugin, viewer, ticket, back);
            }
        });
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("ticket-form-title", tag("category", categoryName)))
                        .canCloseWithEscape(true)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(messages.get("ticket-form-send"), null, 150, send),
                        ActionButton.create(messages.get(back == null ? "ticket-form-cancel" : "ticket-form-back"), null, 150,
                                backAction(plugin, viewer, back)))));
        show(viewer, dialog);
    }

    /** The reported player's head with "You are reporting X". */
    private static DialogBody targetBody(MRAdmin plugin, OfflinePlayer target) {
        String name = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        return DialogBody.item(Items.head(target).name(Component.text(name)).build())
                .description(DialogBody.plainMessage(plugin.messages().get("ticket-form-player-target", tag("player", name)), BUBBLE))
                .showTooltip(false).build();
    }

    /** Asks for the name of a player who is not online (or not in the list) to report them. */
    public static void playerName(MRAdmin plugin, Player viewer, Settings.TicketCategory category, String text,
                                  Runnable back) {
        playerName(plugin, viewer, category, text, "", back);
    }

    private static void playerName(MRAdmin plugin, Player viewer, Settings.TicketCategory category, String text,
                                   String initial, Runnable back) {
        Messages messages = plugin.messages();
        DialogAction next = action(plugin, viewer, response -> {
            String name = text(response.getText(NAME)).trim();
            OfflinePlayer target = name.isEmpty() ? null : Bukkit.getPlayerExact(name);
            if (target == null && !name.isEmpty()) {
                target = Bukkit.getOfflinePlayerIfCached(name);
            }
            if (target == null) {
                messages.send(viewer, "player-not-found", tag("player", name.isEmpty() ? "?" : name));
                playerName(plugin, viewer, category, text, name, back);
            } else if (target.getUniqueId().equals(viewer.getUniqueId())) {
                messages.send(viewer, "ticket-self");
                playerName(plugin, viewer, category, text, "", back);
            } else {
                create(plugin, viewer, category, target, text, back);
            }
        });
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("ticket-name-title"))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(messages.get("ticket-name-hint"), WIDTH)))
                        .inputs(List.of(DialogInput.text(NAME, messages.get("ticket-name-label"))
                                .width(200).maxLength(16).initial(cut(initial, 16)).build()))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(messages.get("ticket-name-next"), null, 150, next),
                        ActionButton.create(messages.get("ticket-form-back"), null, 150, backAction(plugin, viewer, back)))));
        show(viewer, dialog);
    }

    // -------------------------------------------------------------------- view

    /**
     * A ticket with its conversation, the reply field (while open) and the actions. Marks it as read for the viewer.
     *
     * @param back where "Back" leads (e.g. the ticket list); null = the button just closes the dialog
     */
    public static void view(MRAdmin plugin, Player viewer, Ticket ticket, Runnable back) {
        view(plugin, viewer, ticket, "", back);
    }

    private static void view(MRAdmin plugin, Player viewer, Ticket ticket, String draft, Runnable back) {
        TicketManager tickets = plugin.tickets();
        if (!tickets.canView(viewer, ticket)) {
            plugin.messages().send(viewer, "no-permission");
            return;
        }
        tickets.markRead(viewer, ticket);
        Messages messages = plugin.messages();
        Settings.TicketSettings s = plugin.settings().tickets();
        boolean staff = viewer.hasPermission(at.marcelr.mradmin.Perms.TICKETS_VIEW);

        List<DialogBody> body = new ArrayList<>();
        body.add(header(plugin, ticket, staff));
        if (ticket.teamCloseOnly() && ticket.isCreator(viewer.getUniqueId())) {
            body.add(DialogBody.plainMessage(messages.get("ticket-lock-hint"), WIDTH));
        }
        body.add(DialogBody.plainMessage(messages.get("ticket-chat-divider"), WIDTH));
        List<Ticket.Message> all = ticket.messages();
        int from = Math.max(0, all.size() - s.shownMessages());
        if (from > 0) {
            body.add(DialogBody.plainMessage(messages.get("ticket-chat-older", tag("count", from)), WIDTH));
        }
        for (List<Ticket.Message> group : groups(all.subList(from, all.size()))) {
            body.add(bubble(plugin, viewer, group));
        }
        if (!ticket.open()) {
            body.add(DialogBody.plainMessage(messages.get("ticket-chat-closed",
                    tag("admin", ticket.closedBy()), tag("time", Texts.at(plugin, ticket.closedAt()))), WIDTH));
        }

        List<DialogInput> inputs = new ArrayList<>();
        List<ActionButton> buttons = new ArrayList<>();
        Runnable again = () -> {
            Ticket current = tickets.get(ticket.id());
            if (current != null) {
                view(plugin, viewer, current, back);
            }
        };
        boolean creator = ticket.isCreator(viewer.getUniqueId());
        boolean mayReply = creator || viewer.hasPermission(at.marcelr.mradmin.Perms.TICKETS_REPLY);
        boolean mayClose = (creator && !ticket.teamCloseOnly()) || viewer.hasPermission(at.marcelr.mradmin.Perms.TICKETS_CLOSE);
        if (ticket.open() && mayReply) {
            inputs.add(DialogInput.text(TEXT, messages.get("ticket-view-reply"))
                    .width(WIDTH).maxLength(s.maxTextLength()).initial(cut(draft, s.maxTextLength()))
                    .multiline(TextDialogInput.MultilineOptions.create(8, 80)).build());
            buttons.add(ActionButton.create(messages.get("ticket-view-send"), null, 150, action(plugin, viewer, response -> {
                String text = text(response.getText(TEXT));
                if (tickets.reply(viewer, ticket, text)) {
                    again.run();
                } else {
                    view(plugin, viewer, ticket, text, back);
                }
            })));
        }
        if (ticket.open()) {
            if (mayClose) {
                buttons.add(ActionButton.create(messages.get("ticket-view-close"), messages.get("ticket-view-close-hover"), 150,
                        action(plugin, viewer, response -> confirmClose(plugin, viewer, ticket, text(response.getText(TEXT)), back))));
            }
            if (!creator && viewer.hasPermission(at.marcelr.mradmin.Perms.TICKETS_CLAIM)) {
                boolean mine = viewer.getUniqueId().equals(ticket.claimedBy());
                buttons.add(ActionButton.create(messages.get(mine ? "ticket-view-release" : "ticket-view-claim"), null, 150,
                        action(plugin, viewer, response -> {
                            tickets.toggleClaim(viewer, ticket);
                            view(plugin, viewer, ticket, text(response.getText(TEXT)), back);
                        })));
            }
        } else if (viewer.hasPermission(at.marcelr.mradmin.Perms.TICKETS_REOPEN)) {
            buttons.add(ActionButton.create(messages.get("ticket-view-reopen"), null, 150, action(plugin, viewer, response -> {
                tickets.reopen(viewer, ticket);
                again.run();
            })));
        }
        // Requests from the web forms have no place in the world.
        if (ticket.request() == null && viewer.hasPermission(at.marcelr.mradmin.Perms.TICKETS_TELEPORT)
                && plugin.feature(at.marcelr.mradmin.Feature.TELEPORT)) {
            buttons.add(ActionButton.create(messages.get("ticket-view-teleport"), messages.get("ticket-view-teleport-hover"), 150,
                    action(plugin, viewer, response -> tickets.teleport(viewer, ticket))));
        }
        Settings.HubButton source = staff ? sourcePlugin(plugin, ticket) : null;
        if (source != null && plugin.modules().hasHealth(source)) {
            buttons.add(ActionButton.create(messages.get("ticket-view-object"), messages.get("ticket-view-object-hover"), 150,
                    action(plugin, viewer, response -> plugin.modules().openHealth(source, viewer, ticket.sourceId(), again))));
        }
        ActionButton exit = ActionButton.create(messages.get(back == null ? "ticket-view-exit" : "ticket-view-back"), null, 150,
                back == null ? null : backAction(plugin, viewer, back));
        // A closed ticket offers its creator nothing to do (no reply, no reopen) - a dialog with several buttons needs
        // at least one, so it is shown read-only with just "Back".
        DialogType type = buttons.isEmpty()
                ? DialogType.notice(exit)
                : DialogType.multiAction(buttons).columns(2).exitAction(exit).build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("ticket-chat-title", tag("id", ticket.id()),
                                tag("type", tickets.categoryName(ticket.category()))))
                        .canCloseWithEscape(true)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(type));
        show(viewer, dialog);
    }

    /**
     * All details in one block at the top: the reported player's head (or the type's icon) with the facts next to it.
     * The text beside an item is left-aligned, unlike plain dialog text, which Minecraft centres.
     */
    private static DialogBody header(MRAdmin plugin, Ticket ticket, boolean staff) {
        Messages messages = plugin.messages();
        TicketManager tickets = plugin.tickets();
        Component status = messages.get(ticket.open() ? "ticket-status-open" : "ticket-status-closed");
        Component claimed = ticket.claimedByName() == null ? messages.get("ticket-view-unclaimed")
                : Component.text(staff || plugin.settings().tickets().showStaffNames() ? ticket.claimedByName()
                : messages.plain("ticket-staff-anonymous"));
        TagResolver[] common = new TagResolver[]{
                tag("id", ticket.id()), tag("type", tickets.categoryName(ticket.category())),
                tag("status", status), tag("claimed", claimed), tag("creator", ticket.creatorName()),
                tag("created", Texts.at(plugin, ticket.created())), tag("subject", ticket.subject()),
                tag("player", ticket.targetName() == null ? "" : ticket.targetName()),
                tag("category", tickets.categoryName(ticket.category())),
                tag("online", messages.get(ticket.target() != null && Bukkit.getPlayer(ticket.target()) != null
                        ? "gui-online" : "gui-offline")),
                tag("world", ticket.world()), tag("x", ticket.blockX()), tag("y", ticket.blockY()), tag("z", ticket.blockZ())};
        Component text = messages.get(ticket.playerReport() ? "ticket-chat-header-player" : "ticket-chat-header-other", common);
        if (staff && messages.has("ticket-chat-header-staff")) {
            text = text.appendNewline().append(messages.get("ticket-chat-header-staff", common));
        }
        Integration source = ticket.source() == null ? null : Integration.byPlugin(ticket.source());
        ItemStack icon = ticket.playerReport()
                ? Items.head(Bukkit.getOfflinePlayer(ticket.target())).build()
                : new ItemStack(source != null ? source.material() : plugin.settings().tickets().icon());
        return DialogBody.item(icon).description(DialogBody.plainMessage(text, BUBBLE))
                .showTooltip(false).showDecorations(false).build();
    }

    /** Messages in a row by the same author within a few minutes form one bubble, like in a chat app. */
    private static List<List<Ticket.Message>> groups(List<Ticket.Message> messages) {
        List<List<Ticket.Message>> groups = new ArrayList<>();
        for (Ticket.Message message : messages) {
            List<Ticket.Message> last = groups.isEmpty() ? null : groups.getLast();
            if (last != null && last.getFirst().author().equals(message.author())
                    && last.getFirst().staff() == message.staff()
                    && Duration.between(last.getLast().time(), message.time()).compareTo(GROUP_GAP) <= 0) {
                last.add(message);
            } else {
                groups.add(new ArrayList<>(List.of(message)));
            }
        }
        return groups;
    }

    /** One chat bubble: the author's head (or the team icon), name and time, then the text. */
    private static DialogBody bubble(MRAdmin plugin, Player viewer, List<Ticket.Message> group) {
        Messages messages = plugin.messages();
        Ticket.Message first = group.getFirst();
        Component text = Component.join(JoinConfiguration.newlines(),
                group.stream().map(m -> Component.text(m.text())).toList());
        Component bubble = messages.get(first.staff() ? "ticket-chat-message-team" : "ticket-chat-message-player",
                tag("name", plugin.tickets().authorName(first, viewer)),
                tag("time", Texts.at(plugin, first.time())),
                tag("text", text));
        boolean anonymous = first.staff() && !plugin.settings().tickets().showStaffNames()
                && !viewer.hasPermission(at.marcelr.mradmin.Perms.TICKETS_VIEW);
        ItemStack avatar;
        if (TicketManager.SYSTEM.equals(first.author())) {
            // Written by a plugin (e.g. MRchunks about a lock): its icon instead of a head.
            Integration integration = Integration.byPlugin(first.authorName());
            avatar = new ItemStack(integration != null ? integration.material() : plugin.settings().tickets().teamIcon());
        } else {
            avatar = anonymous ? new ItemStack(plugin.settings().tickets().teamIcon())
                    : Items.head(Bukkit.getOfflinePlayer(first.author())).build();
        }
        return DialogBody.item(avatar).description(DialogBody.plainMessage(bubble, BUBBLE))
                .showTooltip(false).showDecorations(false).build();
    }

    /** The connected plugin a ticket comes from (MRchunks for chunk loader locks), or null. */
    private static Settings.HubButton sourcePlugin(MRAdmin plugin, Ticket ticket) {
        if (ticket.source() == null) {
            return null;
        }
        return plugin.modules().buttons().stream()
                .filter(b -> b.plugin().equalsIgnoreCase(ticket.source()) && (plugin.modules().module(b) != null))
                .findFirst().orElse(null);
    }

    private static void confirmClose(MRAdmin plugin, Player viewer, Ticket ticket, String text, Runnable back) {
        Messages messages = plugin.messages();
        TicketManager tickets = plugin.tickets();
        boolean withText = !text.isBlank();
        DialogAction yes = action(plugin, viewer, response -> {
            if (withText && !tickets.reply(viewer, ticket, text)) {
                view(plugin, viewer, ticket, text, back);
                return;
            }
            tickets.close(viewer, ticket);
            if (back != null) {
                back.run();
            }
        });
        DialogAction no = action(plugin, viewer, response -> view(plugin, viewer, ticket, text, back));
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("ticket-close-title", tag("id", ticket.id())))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(messages.get(withText ? "ticket-close-hint-text" : "ticket-close-hint",
                                tag("id", ticket.id())), WIDTH)))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(messages.get("ticket-close-yes"), null, 150, yes),
                        ActionButton.create(messages.get("ticket-close-no"), null, 150, no))));
        show(viewer, dialog);
    }

    // ----------------------------------------------------------------- helpers

    /** A button action that runs on the main thread (clicks may arrive on the network thread), if still online. */
    private static DialogAction action(MRAdmin plugin, Player viewer, Consumer<DialogResponseView> run) {
        return DialogAction.customClick((response, audience) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) {
                run.accept(response);
            }
        }), ONCE);
    }

    private static DialogAction backAction(MRAdmin plugin, Player viewer, Runnable back) {
        return back == null ? null : action(plugin, viewer, response -> back.run());
    }

    private static void show(Player viewer, Dialog dialog) {
        viewer.closeInventory();
        viewer.showDialog(dialog);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    /** Initial values must not be longer than the input allows, otherwise the client rejects the dialog. */
    private static String cut(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max);
    }
}

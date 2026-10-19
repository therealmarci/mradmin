package at.marcelr.mradmin.gui;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Texts;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.ticket.TicketManager;
import at.marcelr.mradmin.util.TicketText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;

import static at.marcelr.mradmin.Messages.tag;

/** The item of a ticket in the player's and in the team's ticket menu. */
final class TicketItems {

    private TicketItems() {
    }

    /** @param staffView team menu (creator, claimer, teleport hint) instead of the player's own list */
    static ItemStack ticket(MRAdmin plugin, Ticket ticket, boolean staffView) {
        Messages messages = plugin.messages();
        TicketManager tickets = plugin.tickets();
        boolean unread = staffView ? ticket.open() && ticket.staffUnread() : ticket.creatorUnread();
        Ticket.Message last = ticket.lastMessage();
        String claimed = ticket.claimedByName();
        if (claimed != null && !staffView && !plugin.settings().tickets().showStaffNames()) {
            claimed = messages.plain("ticket-staff-anonymous");
        }
        TagResolver[] resolvers = {
                tag("id", ticket.id()),
                tag("type", tickets.categoryName(ticket.category())),
                tag("summary", TicketText.preview(tickets.summary(ticket), 40)),
                tag("creator", ticket.creatorName()),
                tag("claimed", claimed == null ? messages.get("ticket-view-unclaimed") : Component.text(claimed)),
                tag("status", messages.get(ticket.open() ? "ticket-status-open" : "ticket-status-closed")),
                tag("count", ticket.messages().size()),
                tag("last", last == null ? "" : TicketText.preview(last.text(), 40)),
                tag("ago", Texts.duration(Instant.now().toEpochMilli() - ticket.lastActivity().toEpochMilli())),
                tag("time", Texts.at(plugin, ticket.created()))};
        Items item = Items.of(plugin.settings().tickets().icon())
                .name(messages.item(ticket.open() ? "gui-ticket-name" : "gui-ticket-name-closed", resolvers));
        if (unread) {
            item.lore(messages.lore(staffView ? "gui-ticket-unread-staff" : "gui-ticket-unread", resolvers));
        }
        item.lore(messages.lore(staffView ? "gui-ticket-lore-staff" : "gui-ticket-lore", resolvers));
        if (staffView && plugin.feature(at.marcelr.mradmin.Feature.TELEPORT)) {
            item.lore(messages.item("gui-ticket-teleport-hint"));
        }
        return item.glint(unread).build();
    }
}

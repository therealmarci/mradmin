package at.marcelr.mradmin.gui;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.ticket.TicketManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

import static at.marcelr.mradmin.Messages.tag;

/**
 * /report - the player's ticket menu:
 * <pre>
 *  row 0:   border
 *  row 1:   create a ticket, the player's head with their counts, archive (closed tickets)
 *  row 2:   border
 *  row 3-4: open tickets - or, in the archive, the closed ones (click opens the ticket)
 *  row 5:   pages, close
 * </pre>
 */
public final class TicketMenu extends Menu {

    private static final int PER_PAGE = 18;
    private static final int FIRST = 27;
    private boolean archive;
    private int page;

    public TicketMenu(MRAdmin plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.messages().get("gui-title-my-tickets"));
    }

    @Override
    protected void draw() {
        Messages messages = plugin.messages();
        TicketManager tickets = plugin.tickets();
        List<Ticket> mine = tickets.byCreator(viewer.getUniqueId());
        List<Ticket> open = mine.stream().filter(Ticket::open).toList();
        List<Ticket> closed = mine.stream().filter(t -> !t.open()).toList();
        long unread = mine.stream().filter(t -> t.creatorUnread()).count();
        long unreadClosed = closed.stream().filter(t -> t.creatorUnread()).count();

        if (tickets.mayCreate(viewer)) {
            set(11, Items.of(Material.WRITABLE_BOOK).name(messages.item("gui-new-ticket"))
                    .lore(messages.lore("gui-new-ticket-lore")).build(), click -> {
                if (!tickets.canStart(viewer)) {
                    sound(Sound.ENTITY_VILLAGER_NO);
                    return;
                }
                clickSound();
                TicketDialog.start(plugin, viewer, this::open);
            });
        } else {
            set(11, Items.of(Material.BARRIER).name(messages.item("gui-new-ticket-none")).build());
        }
        set(13, Items.head(viewer).name(messages.item("gui-my-tickets-head", tag("player", viewer.getName())))
                .lore(messages.lore("gui-my-tickets-head-lore", tag("open", open.size()), tag("unread", unread),
                        tag("closed", closed.size())))
                .glint(unread > 0).build());
        if (archive) {
            set(15, Items.of(Material.ENDER_CHEST).name(messages.item("gui-ticket-archive-back"))
                    .lore(messages.lore("gui-ticket-archive-back-lore", tag("count", open.size()))).build(),
                    click -> switchTo(false));
        } else {
            set(15, Items.of(Material.CHEST).name(messages.item("gui-ticket-archive"))
                    .lore(messages.lore("gui-ticket-archive-lore", tag("count", closed.size()), tag("unread", unreadClosed)))
                    .glint(unreadClosed > 0).build(), click -> switchTo(true));
        }

        List<Ticket> shown = archive ? closed : open;
        int pages = Pages.count(shown.size(), PER_PAGE);
        page = Math.min(page, pages - 1);
        if (shown.isEmpty()) {
            set(FIRST + 4, Items.of(archive ? Material.GRAY_DYE : Material.LIME_DYE)
                    .name(messages.item(archive ? "gui-archive-empty" : "gui-my-tickets-empty"))
                    .lore(messages.lore(archive ? "gui-archive-empty-lore" : "gui-my-tickets-empty-lore")).build());
        }
        int slot = FIRST;
        for (Ticket ticket : Pages.slice(shown, page, PER_PAGE)) {
            set(slot++, TicketItems.ticket(plugin, ticket, false), click -> {
                clickSound();
                TicketDialog.view(plugin, viewer, ticket, this::open);
            });
        }

        fill(0);
        fill(1);
        fill(2);
        fillRow(5);
        Pages.navigation(this, messages, page, pages, p -> page = p);
        set(49, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());
    }

    private void switchTo(boolean archive) {
        this.archive = archive;
        page = 0;
        clickSound();
        render();
    }

    private void fill(int row) {
        for (int slot = row * 9; slot < row * 9 + 9; slot++) {
            if (getInventory().getItem(slot) == null) {
                set(slot, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
            }
        }
    }
}

package at.marcelr.mradmin.gui;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.ticket.Ticket;
import at.marcelr.mradmin.ticket.TicketManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.UUID;

import static at.marcelr.mradmin.Messages.tag;

/**
 * Tickets for the team: open or closed, all categories or one. Left-click opens the ticket (conversation, answer, claim,
 * close), right-click teleports to the reported player or to where the ticket was opened.
 * <pre>
 *  row 0-4: tickets, unread ones first
 *  row 5:   pages (45/53), category filter (46), back (48), close, open/closed (52)
 * </pre>
 */
public final class TicketsMenu extends Menu {

    private static final int PER_PAGE = 45;
    private final UUID about;
    private final Runnable back;
    /** Category id, null = all categories */
    private String category;
    private boolean open = true;
    private int page;

    /** @param about only player tickets about this player, or null for all; {@code back} null = no back button */
    public TicketsMenu(MRAdmin plugin, Player viewer, UUID about, Runnable back) {
        super(plugin, viewer, 6, about == null ? plugin.messages().get("gui-title-tickets")
                : plugin.messages().get("gui-title-tickets-player", tag("player", name(about))));
        this.about = about;
        this.back = back;
    }

    private static String name(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? id.toString() : name;
    }

    @Override
    protected void draw() {
        Messages messages = plugin.messages();
        TicketManager tickets = plugin.tickets();
        List<Ticket> shown = tickets.list(category, open, about);
        int pages = Pages.count(shown.size(), PER_PAGE);
        page = Math.min(page, pages - 1);
        if (shown.isEmpty()) {
            set(22, Items.of(Material.LIME_DYE).name(messages.item(open ? "gui-tickets-empty" : "gui-tickets-empty-closed")).build());
        }
        int slot = 0;
        for (Ticket ticket : Pages.slice(shown, page, PER_PAGE)) {
            set(slot++, TicketItems.ticket(plugin, ticket, true), click -> {
                if (!allowed(TicketManager.PERM_MANAGE)) {
                    return;
                }
                clickSound();
                if ((click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) && plugin.feature(at.marcelr.mradmin.Feature.TELEPORT)) {
                    viewer.closeInventory();
                    tickets.teleport(viewer, ticket);
                } else {
                    TicketDialog.view(plugin, viewer, ticket, this::open);
                }
            });
        }
        fillRow(5);
        Pages.navigation(this, messages, page, pages, p -> page = p);

        if (about == null) {
            set(46, Items.of(Material.HOPPER).name(messages.item("gui-tickets-filter-type",
                            tag("type", category == null ? messages.plain("gui-tickets-filter-all") : tickets.categoryName(category))))
                    .lore(messages.lore("gui-tickets-filter-type-lore")).glint(category != null).build(), click -> {
                category = next(category);
                page = 0;
                clickSound();
                render();
            });
        }
        set(52, Items.of(open ? Material.WRITABLE_BOOK : Material.BOOK)
                .name(messages.item(open ? "gui-tickets-filter-open" : "gui-tickets-filter-closed"))
                .lore(messages.lore("gui-tickets-filter-status-lore", tag("open", tickets.openCount()),
                        tag("unread", tickets.staffUnreadCount()))).build(), click -> {
            open = !open;
            page = 0;
            clickSound();
            render();
        });
        if (back != null) {
            set(48, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
                clickSound();
                back.run();
            });
        }
        // Without "back" the close button sits in the middle, otherwise mirrored to it.
        set(back != null ? 50 : 49, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());
    }

    /** all -> every configured category in turn -> all */
    private String next(String current) {
        List<String> ids = plugin.settings().tickets().categories().stream().map(Settings.TicketCategory::id).toList();
        int index = current == null ? -1 : ids.indexOf(current);
        return index + 1 < ids.size() ? ids.get(index + 1) : null;
    }
}

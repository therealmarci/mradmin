package at.marcelr.mradmin.gui;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

import static at.marcelr.mradmin.Messages.tag;

/** Choosing the player to report: heads of the online players, or a name typed into a dialog (also offline). */
public final class TicketPlayerMenu extends Menu {

    private static final int PER_PAGE = 45;
    private final Settings.TicketCategory category;
    private final String text;
    private final Runnable back;
    private int page;

    /** @param text prefilled description (e.g. from /report &lt;player&gt; &lt;text&gt;), may be null */
    public TicketPlayerMenu(MRAdmin plugin, Player viewer, Settings.TicketCategory category, String text, Runnable back) {
        super(plugin, viewer, 6, plugin.messages().get("gui-title-ticket-player"));
        this.category = category;
        this.text = text;
        this.back = back;
    }

    @Override
    protected void draw() {
        Messages messages = plugin.messages();
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(viewer) && viewer.canSee(p))
                .map(p -> (Player) p)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int pages = Pages.count(players.size(), PER_PAGE);
        page = Math.min(page, pages - 1);
        if (players.isEmpty()) {
            set(22, Items.of(Material.GRAY_DYE).name(messages.item("gui-ticket-player-empty")).build());
        }
        int slot = 0;
        for (Player target : Pages.slice(players, page, PER_PAGE)) {
            set(slot++, Items.head(target).name(messages.item("gui-ticket-player-name", tag("player", target.getName())))
                    .lore(messages.lore("gui-ticket-player-lore")).build(), click -> {
                clickSound();
                TicketDialog.create(plugin, viewer, category, target, text, this::open);
            });
        }
        fillRow(5);
        Pages.navigation(this, messages, page, pages, p -> page = p);
        set(48, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
            clickSound();
            back.run();
        });
        set(49, Items.of(Material.NAME_TAG).name(messages.item("gui-ticket-player-other"))
                .lore(messages.lore("gui-ticket-player-other-lore")).build(), click -> {
            clickSound();
            TicketDialog.playerName(plugin, viewer, category, text, this::open);
        });
        set(50, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());
    }
}

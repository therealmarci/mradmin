package at.marcelr.mradmin.gui;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Texts;
import at.marcelr.mradmin.report.ReportManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static at.marcelr.mradmin.Messages.tag;

/** Open reports (all, or about one player). Left-click teleports, right-click marks as done. */
public final class ReportsMenu extends Menu {

    private static final int PER_PAGE = 45;
    private final UUID about;
    private final Runnable back;
    private int page;

    /** @param about only reports about this player, or null for all; {@code back} null = no back button */
    public ReportsMenu(MRAdmin plugin, Player viewer, UUID about, Runnable back) {
        super(plugin, viewer, 6, about == null ? plugin.messages().get("gui-title-reports")
                : plugin.messages().get("gui-title-reports-player", tag("player", name(about))));
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
        ReportManager reports = plugin.reports();
        List<ReportManager.Report> open = reports.open(about);
        int pages = Pages.count(open.size(), PER_PAGE);
        page = Math.min(page, pages - 1);
        if (open.isEmpty()) {
            set(22, Items.of(Material.LIME_DYE).name(messages.item("gui-reports-empty")).build());
        }
        int slot = 0;
        for (ReportManager.Report r : Pages.slice(open, page, PER_PAGE)) {
            boolean online = Bukkit.getPlayer(r.target()) != null;
            set(slot++, Items.of(online ? Material.WRITABLE_BOOK : Material.PAPER)
                    .name(messages.item("gui-report-name", tag("id", r.id()), tag("player", r.targetName())))
                    .lore(messages.lore("gui-report-lore",
                            tag("reporter", r.reporterName()),
                            tag("reason", r.reason()),
                            tag("ago", Texts.duration(Instant.now().toEpochMilli() - r.time().toEpochMilli())),
                            tag("time", Texts.at(plugin, r.time())),
                            tag("world", r.world()), tag("x", (int) r.x()), tag("y", (int) r.y()), tag("z", (int) r.z()),
                            tag("online", messages.get(online ? "gui-online" : "gui-offline"))))
                    .build(), click -> {
                if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                    if (allowed(ReportManager.PERM_MANAGE)) {
                        reports.close(viewer, r);
                        successSound();
                        render();
                    }
                } else if (allowed(ReportManager.PERM_MANAGE)) {
                    viewer.closeInventory();
                    reports.teleport(viewer, r);
                }
            });
        }
        fillRow(5);
        Pages.navigation(this, messages, page, pages, p -> page = p);
        if (back != null) {
            set(48, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
                clickSound();
                back.run();
            });
        }
        // Without "back" the close button sits in the middle, otherwise mirrored to it.
        set(back != null ? 50 : 49, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());
    }
}

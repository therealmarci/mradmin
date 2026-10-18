package at.marcelr.mradmin.gui;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.announce.Announcer;
import at.marcelr.mradmin.util.Layout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static at.marcelr.mradmin.Messages.tag;

/**
 * All announcement presets as buttons, plus an own text. A preset with values (e.g. restart "5 Minuten") asks for
 * them in a dialog first. Right-click announces without posting to the community Discord.
 * <pre>
 *  row 0:   border
 *  row 1-3: presets, up to 7 per row, each row centered
 *  row 4:   back, own text, close
 * </pre>
 */
public final class AnnounceMenu extends Menu {

    private static final int PER_ROW = 7;
    private static final int MAX = PER_ROW * 3;

    private final Runnable back;

    public AnnounceMenu(MRAdmin plugin, Player viewer, Runnable back) {
        super(plugin, viewer, 5, plugin.messages().get("gui-title-announce"));
        this.back = back;
    }

    @Override
    protected void draw() {
        Messages messages = plugin.messages();
        Announcer announcer = plugin.announcer();
        List<String> presets = announcer.presets().stream().limit(MAX).toList();
        // Placeholders are shown as "…" in the preview.
        TagResolver[] preview = new TagResolver[10];
        preview[0] = tag("args", "…");
        for (int i = 1; i <= 9; i++) {
            preview[i] = tag("arg" + i, "…");
        }

        for (int row = 0; row * PER_ROW < presets.size(); row++) {
            List<String> inRow = presets.subList(row * PER_ROW, Math.min(presets.size(), (row + 1) * PER_ROW));
            int[] slots = Layout.centered(row + 1, inRow.size());
            for (int i = 0; i < inRow.size(); i++) {
                String preset = inRow.get(i);
                String base = Announcer.PRESETS + "." + preset + ".";
                boolean values = announcer.needsValues(preset);
                boolean discord = announcer.postsToDiscord(preset);
                Items item = Items.of(values ? Material.WRITABLE_BOOK : Material.PAPER)
                        .name(messages.item(base + "title", preview))
                        .lore(messages.item("gui-announce-preset-id", tag("preset", preset)));
                if (messages.has(base + "subtitle")) {
                    item.lore(messages.item(base + "subtitle", preview));
                }
                item.lore(Component.empty())
                        .lore(messages.item(values ? "gui-announce-values-yes" : "gui-announce-values-no"))
                        .lore(messages.item(discord ? "gui-announce-discord-yes" : "gui-announce-discord-no"))
                        .lore(Component.empty())
                        .lore(messages.item("gui-announce-click"));
                if (discord) {
                    item.lore(messages.item("gui-announce-click-quiet"));
                }
                set(slots[i], item.build(), click -> {
                    boolean toDiscord = discord && click != ClickType.RIGHT && click != ClickType.SHIFT_RIGHT;
                    clickSound();
                    if (values) {
                        AnnounceDialog.values(plugin, viewer, preset, toDiscord, this::reopen);
                    } else {
                        viewer.closeInventory();
                        announcer.announcePreset(preset, List.of(), viewer.getName(), toDiscord);
                        successSound();
                    }
                });
            }
        }
        if (presets.isEmpty()) {
            set(22, Items.of(Material.STRUCTURE_VOID).name(messages.item("gui-announce-none")).build());
        }

        int[] nav = Layout.centered(4, 3);
        set(nav[0], Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
            clickSound();
            back.run();
        });
        set(nav[1], Items.of(Material.OAK_SIGN).name(messages.item("gui-announce-custom-name"))
                .lore(messages.lore("gui-announce-custom-lore")).build(), click -> {
            clickSound();
            AnnounceDialog.custom(plugin, viewer, this::reopen);
        });
        set(nav[2], Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());

        for (int row = 0; row < 5; row++) {
            ItemStack filler = Items.filler(row == 0 || row == 4 ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
            for (int slot = row * 9; slot < row * 9 + 9; slot++) {
                if (getInventory().getItem(slot) == null) {
                    set(slot, filler);
                }
            }
        }
    }

    private void reopen() {
        new AnnounceMenu(plugin, viewer, back).open();
    }
}

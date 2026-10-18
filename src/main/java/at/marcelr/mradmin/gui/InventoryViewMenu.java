package at.marcelr.mradmin.gui;

import at.marcelr.mradmin.MRAdmin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

import static at.marcelr.mradmin.Messages.tag;

/**
 * Read-only view of a player's inventory or ender chest, refreshed live. Read-only on purpose: in admin mode the
 * viewer's own inventory is temporary, items taken from someone else would be lost when admin mode ends.
 * <pre>
 *  inventory (6 rows):  rows 0-2 main inventory, row 3 hotbar, row 4 armour + off hand (centered), row 5 navigation
 *  ender chest (4 rows): rows 0-2 ender chest, row 3 navigation
 * </pre>
 */
public final class InventoryViewMenu extends Menu {

    private final UUID targetId;
    private final boolean enderChest;
    private final Runnable back;

    public InventoryViewMenu(MRAdmin plugin, Player viewer, Player target, boolean enderChest, Runnable back) {
        super(plugin, viewer, enderChest ? 4 : 6, plugin.messages().get(enderChest ? "gui-title-enderchest" : "gui-title-inventory",
                tag("player", target.getName())));
        this.targetId = target.getUniqueId();
        this.enderChest = enderChest;
        this.back = back;
    }

    @Override
    protected void draw() {
        Player target = Bukkit.getPlayer(targetId);
        int nav = enderChest ? 27 : 45;
        if (target == null) {
            set(13, Items.of(Material.BARRIER).name(plugin.messages().item("gui-player-gone")).build());
        } else if (enderChest) {
            ItemStack[] items = target.getEnderChest().getContents();
            for (int i = 0; i < Math.min(27, items.length); i++) {
                copy(i, items[i]);
            }
        } else {
            PlayerInventory inv = target.getInventory();
            ItemStack[] storage = inv.getStorageContents();
            for (int i = 9; i < 36 && i < storage.length; i++) {
                copy(i - 9, storage[i]);
            }
            for (int i = 0; i < 9 && i < storage.length; i++) {
                copy(27 + i, storage[i]);
            }
            // armour and off hand centered in row 4
            copy(38, inv.getHelmet());
            copy(39, inv.getChestplate());
            copy(40, inv.getLeggings());
            copy(41, inv.getBoots());
            copy(42, inv.getItemInOffHand());
        }
        set(nav + 3, Items.of(Material.ARROW).name(plugin.messages().item("gui-back")).build(), click -> {
            clickSound();
            back.run();
        });
        Items info = Items.of(target == null ? Material.PAPER : Material.EXPERIENCE_BOTTLE)
                .name(plugin.messages().item("gui-inventory-readonly"));
        if (target != null) {
            info.lore(plugin.messages().item("gui-inventory-xp", tag("level", target.getLevel())));
        }
        set(nav + 4, info.build());
        set(nav + 5, Items.of(Material.BARRIER).name(plugin.messages().item("gui-close")).build(), click -> viewer.closeInventory());
        fillRow(nav / 9);
        if (!enderChest) {
            fillRow(4);
        }
    }

    private void copy(int slot, ItemStack item) {
        if (item != null && !item.isEmpty()) {
            set(slot, item.clone());
        }
    }
}

package at.marcelr.mradmin.gui;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.Texts;
import at.marcelr.mradmin.mode.AdminModeManager;
import at.marcelr.mradmin.player.FreezeManager;
import at.marcelr.mradmin.util.Layout;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import static at.marcelr.mradmin.Messages.tag;

/**
 * Everything about one online player, every row centered:
 * <pre>
 *  row 0:  head with info (middle)
 *  row 1:  teleport, summon, inventory, ender chest, freeze, tickets about the player
 *  row 2:  plugins with a hook (MRsleep, MRchunks ...)
 *  row 3:  border
 *  row 4:  back, close
 * </pre>
 */
public final class PlayerActionMenu extends Menu {

    public static final String PERM_SUMMON = at.marcelr.mradmin.Perms.PLAYER_SUMMON;
    public static final String PERM_INVENTORY = at.marcelr.mradmin.Perms.PLAYER_INVENTORY;

    private record Button(ItemStack item, Consumer<ClickType> action) {
    }

    private final UUID targetId;
    private final Runnable back;

    public PlayerActionMenu(MRAdmin plugin, Player viewer, Player target, Runnable back) {
        super(plugin, viewer, 5, plugin.messages().get("gui-title-player", tag("player", target.getName())));
        this.targetId = target.getUniqueId();
        this.back = back;
    }

    /** Reopens this menu (used as "back" by the menus it opens). */
    private void reopen() {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            back.run();
        } else {
            new PlayerActionMenu(plugin, viewer, target, back).open();
        }
    }

    @Override
    protected void draw() {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            set(22, Items.of(Material.BARRIER).name(plugin.messages().item("gui-player-gone")).build());
        } else {
            set(4, head(target));
            place(1, actions(target));
            place(2, plugins());
        }
        place(4, navigation());
        for (int row = 0; row < 5; row++) {
            fillRow(row);
        }
    }

    private void place(int row, List<Button> buttons) {
        int[] slots = Layout.centered(row, buttons.size());
        for (int i = 0; i < slots.length; i++) {
            set(slots[i], buttons.get(i).item(), buttons.get(i).action());
        }
    }

    private ItemStack head(Player target) {
        Messages messages = plugin.messages();
        Location l = target.getLocation();
        long playtimeTicks = target.getStatistic(Statistic.PLAY_ONE_MINUTE);
        Component status = Component.empty();
        if (plugin.freeze().frozen(targetId)) {
            status = messages.get("gui-player-status-frozen");
        } else if (plugin.adminMode().active(target)) {
            status = messages.get("gui-player-status-admin");
        }
        return Items.head(target).name(messages.item("gui-teleport-player-name", tag("player", target.getName())))
                .lore(messages.lore("gui-player-info",
                        tag("world", l.getWorld().getName()),
                        tag("x", l.getBlockX()), tag("y", l.getBlockY()), tag("z", l.getBlockZ()),
                        tag("gamemode", target.getGameMode().name().toLowerCase(Locale.ROOT)),
                        tag("health", Math.round(target.getHealth())),
                        tag("food", target.getFoodLevel()),
                        tag("level", target.getLevel()),
                        tag("ping", target.getPing()),
                        tag("playtime", Texts.duration(playtimeTicks * 50L)),
                        tag("first-join", Texts.at(plugin, Instant.ofEpochMilli(target.getFirstPlayed()))),
                        tag("status", status)))
                .build();
    }

    private List<Button> actions(Player target) {
        Messages messages = plugin.messages();
        List<Button> actions = new ArrayList<>();
        add(actions, Feature.TELEPORT, action(Material.ENDER_PEARL, "gui-player-teleport", AdminModeManager.PERM_TELEPORT, () -> {
            viewer.closeInventory();
            viewer.teleport(target.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            messages.send(viewer, "mode-teleported", tag("player", target.getName()));
            sound(Sound.ENTITY_ENDERMAN_TELEPORT);
        }));
        add(actions, Feature.SUMMON, action(Material.LEAD, "gui-player-summon", PERM_SUMMON, () -> {
            viewer.closeInventory();
            target.teleport(viewer.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            messages.send(viewer, "player-summoned", tag("player", target.getName()));
            messages.send(target, "player-summoned-target", tag("staff", viewer.getName()));
            sound(Sound.ENTITY_ENDERMAN_TELEPORT);
        }));
        add(actions, Feature.INVENTORY, action(Material.CHEST, "gui-player-inventory", PERM_INVENTORY,
                () -> new InventoryViewMenu(plugin, viewer, target, false, this::reopen).open()));
        add(actions, Feature.INVENTORY, action(Material.ENDER_CHEST, "gui-player-enderchest", PERM_INVENTORY,
                () -> new InventoryViewMenu(plugin, viewer, target, true, this::reopen).open()));
        boolean frozen = plugin.freeze().frozen(targetId);
        add(actions, Feature.FREEZE, action(frozen ? Material.BLUE_ICE : Material.PACKED_ICE, frozen ? "gui-player-unfreeze" : "gui-player-freeze",
                FreezeManager.PERM_FREEZE, () -> {
                    if (target.equals(viewer)) {
                        messages.send(viewer, "freeze-self");
                        return;
                    }
                    plugin.freeze().set(viewer, target, !frozen);
                    render();
                }));
        if (viewer.hasPermission(at.marcelr.mradmin.Perms.TICKETS_VIEW) && plugin.feature(Feature.TICKETS)) {
            int reports = plugin.tickets().openAbout(targetId);
            actions.add(new Button(Items.of(reports > 0 ? Material.WRITABLE_BOOK : Material.BOOK)
                    .name(messages.item("gui-player-tickets", tag("count", reports)))
                    .lore(messages.lore("gui-player-tickets-lore")).glint(reports > 0).build(), click -> {
                clickSound();
                new TicketsMenu(plugin, viewer, targetId, this::reopen).open();
            }));
        }
        return actions;
    }

    /** Adds the button only while its feature is switched on (features in config.yml). */
    private void add(List<Button> buttons, Feature feature, Button button) {
        if (plugin.feature(feature)) {
            buttons.add(button);
        }
    }

    /** Plugins with a hook: their summary for this player, a click opens their player menu. */
    private List<Button> plugins() {
        Messages messages = plugin.messages();
        List<Button> buttons = new ArrayList<>();
        for (Settings.HubButton button : plugin.settings().adminMode().buttons()) {
            if (!plugin.hooks().available(button) || !permitted(button)) {
                continue;
            }
            String base = "gui-hub-buttons." + button.id();
            Component name = messages.has(base + ".name") ? messages.item(base + ".name")
                    : messages.item("gui-hub-button-fallback-name", tag("name", button.id()));
            Items item = Items.of(button.material()).name(name)
                    .lore(plugin.hooks().playerSummary(button, viewer, targetId))
                    .lore(Component.empty()).lore(messages.item("gui-player-plugin-click"));
            buttons.add(new Button(item.build(), click -> {
                clickSound();
                if (!plugin.hooks().openPlayerMenu(button, viewer, targetId, this::reopen)) {
                    sound(Sound.ENTITY_VILLAGER_NO);
                }
            }));
        }
        return buttons;
    }

    private List<Button> navigation() {
        Messages messages = plugin.messages();
        return List.of(
                new Button(Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
                    clickSound();
                    back.run();
                }),
                new Button(Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory()));
    }

    private boolean permitted(Settings.HubButton button) {
        Plugin other = Bukkit.getPluginManager().getPlugin(button.plugin());
        return other != null && other.isEnabled() && (button.permission().isEmpty() || viewer.hasPermission(button.permission()));
    }

    private Button action(Material material, String key, String permission, Runnable action) {
        Messages messages = plugin.messages();
        Items item = Items.of(material).name(messages.item(key + "-name")).lore(messages.lore(key + "-lore"));
        if (!viewer.hasPermission(permission)) {
            item.lore(Component.empty()).lore(messages.item("gui-no-permission"));
        }
        return new Button(item.build(), click -> {
            if (allowed(permission)) {
                clickSound();
                action.run();
            }
        });
    }
}

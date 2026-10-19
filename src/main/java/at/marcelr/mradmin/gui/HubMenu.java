package at.marcelr.mradmin.gui;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.command.MRAdminCommand;
import at.marcelr.mradmin.command.StaffChat;
import at.marcelr.mradmin.maintenance.MaintenanceManager;
import at.marcelr.mradmin.mode.AdminModeManager;
import at.marcelr.mradmin.util.Layout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static at.marcelr.mradmin.Messages.tag;

/**
 * The admin dashboard (/mradmin or the admin compass). Every row is centered:
 * <pre>
 *  row 0:  black border, the viewer's head in the middle (admin mode, staff chat)
 *  row 1:  live tiles - players, server, maintenance, tickets (click opens)
 *  row 2:  status bar - under each tile a pane in green / yellow / red, light grey elsewhere
 *  row 3:  tools - announcements and the plugin buttons (MRsleep, MRchunks ...)
 *  row 4:  admin mode switch, close, staff chat switch
 * </pre>
 */
public final class HubMenu extends Menu {

    /** State of a tile: colour of its name and of the status pane below it. */
    private enum State {
        GOOD(NamedTextColor.GREEN, Material.LIME_STAINED_GLASS_PANE),
        WARN(NamedTextColor.YELLOW, Material.YELLOW_STAINED_GLASS_PANE),
        BAD(NamedTextColor.RED, Material.RED_STAINED_GLASS_PANE);

        final TextColor color;
        final Material pane;

        State(TextColor color, Material pane) {
            this.color = color;
            this.pane = pane;
        }
    }

    private record Tile(ItemStack item, State state, Component stateText, Consumer<ClickType> action) {
    }

    private record Button(ItemStack item, Consumer<ClickType> action) {
    }

    public HubMenu(MRAdmin plugin, Player viewer) {
        super(plugin, viewer, 5, plugin.messages().get("gui-title-hub"));
    }

    @Override
    protected void draw() {
        set(4, head());

        List<Tile> tiles = tiles();
        int[] tileSlots = Layout.centered(1, tiles.size());
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            set(tileSlots[i], tile.item(), tile.action());
            set(tileSlots[i] + 9, Items.of(tile.state().pane).name(tile.stateText()).build());
        }

        place(3, tools());
        place(4, switches());

        fill(0, Material.BLACK_STAINED_GLASS_PANE);
        fill(1, Material.BLACK_STAINED_GLASS_PANE);
        fill(2, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        fill(3, Material.BLACK_STAINED_GLASS_PANE);
        fill(4, Material.BLACK_STAINED_GLASS_PANE);
    }

    private void fill(int row, Material pane) {
        ItemStack filler = Items.filler(pane);
        for (int slot = row * 9; slot < row * 9 + 9; slot++) {
            if (getInventory().getItem(slot) == null) {
                set(slot, filler);
            }
        }
    }

    private void place(int row, List<Button> buttons) {
        int[] slots = Layout.centered(row, buttons.size());
        for (int i = 0; i < slots.length; i++) {
            set(slots[i], buttons.get(i).item(), buttons.get(i).action());
        }
    }

    private void reopen() {
        new HubMenu(plugin, viewer).open();
    }

    // ------------------------------------------------------------------- head

    private ItemStack head() {
        Messages messages = plugin.messages();
        boolean admin = plugin.adminMode().active(viewer);
        boolean staffChat = plugin.staffChat().toggled(viewer);
        return Items.head(viewer).name(messages.item("gui-hub-head-name", tag("player", viewer.getName())))
                .lore(messages.lore("gui-hub-head-lore",
                        tag("admin", messages.get(admin ? "gui-state-on" : "gui-state-off")),
                        tag("staffchat", messages.get(staffChat ? "gui-state-on" : "gui-state-off")),
                        tag("world", viewer.getWorld().getName()),
                        tag("gamemode", viewer.getGameMode().name().toLowerCase(Locale.ROOT))))
                .glint(admin)
                .build();
    }

    // ------------------------------------------------------------------ tiles

    private List<Tile> tiles() {
        Messages messages = plugin.messages();
        List<Tile> tiles = new ArrayList<>();

        if (viewer.hasPermission(at.marcelr.mradmin.Perms.PLAYER_VIEW)) {
            int online = Math.max(0, Bukkit.getOnlinePlayers().size() - 1);
            long frozen = Bukkit.getOnlinePlayers().stream().filter(p -> plugin.freeze().frozen(p.getUniqueId())).count();
            State state = frozen > 0 ? State.WARN : State.GOOD;
            Component text = messages.item(frozen > 0 ? "gui-tile-players-frozen" : "gui-tile-players-ok", tag("frozen", frozen));
            tiles.add(new Tile(Items.of(Material.PLAYER_HEAD)
                    .name(messages.item("gui-tile-players-name").colorIfAbsent(state.color))
                    .lore(messages.lore("gui-tile-players-lore", tag("online", online), tag("frozen", frozen)))
                    .build(), state, text, click -> {
                clickSound();
                new TeleportMenu(plugin, viewer).open();
            }));
        }

        if (viewer.hasPermission(ServerStatusMenu.PERM) && plugin.feature(Feature.SERVER_STATUS)) {
            double tps = Math.min(20, Bukkit.getTPS()[0]);
            State state = tps >= 19.5 ? State.GOOD : tps >= 15 ? State.WARN : State.BAD;
            String formatted = String.format(Locale.ROOT, "%.1f", tps);
            tiles.add(new Tile(Items.of(Material.COMPARATOR)
                    .name(messages.item("gui-tile-server-name").colorIfAbsent(state.color))
                    .lore(messages.lore("gui-tile-server-lore", tag("tps", formatted),
                            tag("mspt", String.format(Locale.ROOT, "%.1f", Bukkit.getAverageTickTime()))))
                    .build(), state, messages.item("gui-tile-server-state", tag("tps", formatted)), click -> {
                clickSound();
                new ServerStatusMenu(plugin, viewer, this::reopen).open();
            }));
        }

        if (plugin.feature(Feature.MAINTENANCE) && viewer.hasPermission(at.marcelr.mradmin.Perms.MAINTENANCE_VIEW)) {
            tiles.add(maintenanceTile());
        }

        if (viewer.hasPermission(at.marcelr.mradmin.Perms.TICKETS_VIEW) && plugin.feature(Feature.TICKETS)) {
            int open = plugin.tickets().openCount();
            int unread = plugin.tickets().staffUnreadCount();
            // Red while players wait for an answer, yellow while tickets are open at all.
            State state = unread > 0 ? State.BAD : open > 0 ? State.WARN : State.GOOD;
            tiles.add(new Tile(Items.of(open > 0 ? Material.WRITABLE_BOOK : Material.BOOK)
                    .name(messages.item("gui-tile-tickets-name").colorIfAbsent(state.color))
                    .lore(messages.lore("gui-tile-tickets-lore", tag("count", open), tag("unread", unread)))
                    .glint(unread > 0).build(), state, messages.item(unread > 0 ? "gui-tile-tickets-unread"
                    : open > 0 ? "gui-tile-tickets-open" : "gui-tile-tickets-none",
                    tag("count", open), tag("unread", unread)), click -> {
                clickSound();
                new TicketsMenu(plugin, viewer, null, this::reopen).open();
            }));
        }
        return tiles;
    }

    private Tile maintenanceTile() {
        Messages messages = plugin.messages();
        MaintenanceManager maintenance = plugin.maintenance();
        State mState = switch (maintenance.phase()) {
            case NONE -> State.GOOD;
            case PLANNED, UPCOMING -> State.WARN;
            case ACTIVE -> State.BAD;
        };
        String phase = maintenance.phase().name().toLowerCase(Locale.ROOT);
        var resolvers = maintenance.resolvers(maintenance.current(), Instant.now());
        Items clock = Items.of(Material.CLOCK)
                .name(messages.item("gui-tile-maintenance-name").colorIfAbsent(mState.color))
                .lore(messages.lore("gui-hub-maintenance-" + phase, resolvers));
        if (viewer.hasPermission(MaintenanceManager.PERM_MANAGE)) {
            clock.lore(messages.lore("gui-hub-maintenance-click"));
        }
        return new Tile(clock.glint(mState == State.BAD).build(), mState,
                messages.item("gui-tile-maintenance-state-" + phase, resolvers), click -> {
            if (allowed(at.marcelr.mradmin.Perms.MAINTENANCE_VIEW)) {
                clickSound();
                viewer.closeInventory();
                viewer.performCommand("mradmin maintenance");
            }
        });
    }

    // ------------------------------------------------------------------ tools

    private List<Button> tools() {
        Messages messages = plugin.messages();
        List<Button> tools = new ArrayList<>();
        if (viewer.hasPermission(MRAdminCommand.PERM_ANNOUNCE) && plugin.feature(Feature.ANNOUNCE)) {
            tools.add(new Button(Items.of(Material.GOAT_HORN).name(messages.item("gui-hub-announce-name"))
                    .lore(messages.lore("gui-hub-announce-lore", tag("count", plugin.announcer().presets().size())))
                    .build(), click -> {
                clickSound();
                new AnnounceMenu(plugin, viewer, this::reopen).open();
            }));
        }
        for (Settings.HubButton button : visibleButtons()) {
            String base = "gui-hub-buttons." + button.id();
            Component name = messages.has(base + ".name") ? messages.item(base + ".name")
                    : messages.item("gui-hub-button-fallback-name", tag("name", button.id()));
            List<Component> lore = messages.has(base + ".lore") ? messages.lore(base + ".lore") : List.of();
            // Live information from the plugin itself (if it offers a hook), above its own description.
            List<Component> live = plugin.hooks().summary(button, viewer);
            Items item = Items.of(button.material()).name(name).lore(live);
            if (!live.isEmpty() && !lore.isEmpty()) {
                item.lore(Component.empty());
            }
            tools.add(new Button(item.lore(lore).build(), click -> {
                clickSound();
                viewer.closeInventory();
                // The other plugin checks its own permissions; we only run its command as the player.
                viewer.performCommand(button.command());
            }));
        }
        return tools;
    }

    /** Buttons whose plugin is installed and enabled and whose permission the viewer has. */
    private List<Settings.HubButton> visibleButtons() {
        return plugin.settings().adminMode().buttons().stream()
                .filter(b -> {
                    if (b.plugin().isEmpty()) {
                        return true;
                    }
                    Plugin other = Bukkit.getPluginManager().getPlugin(b.plugin());
                    return other != null && other.isEnabled();
                })
                .filter(b -> b.permission().isEmpty() || viewer.hasPermission(b.permission()))
                .toList();
    }

    // --------------------------------------------------------------- switches

    private List<Button> switches() {
        Messages messages = plugin.messages();
        List<Button> buttons = new ArrayList<>();
        if (viewer.hasPermission(AdminModeManager.PERM_USE) && plugin.feature(Feature.ADMIN_MODE)) {
            boolean on = plugin.adminMode().active(viewer);
            buttons.add(new Button(Items.of(on ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name(messages.item("gui-switch-admin", tag("state", messages.get(on ? "gui-state-on" : "gui-state-off"))))
                    .lore(messages.lore(on ? "gui-switch-admin-on-lore" : "gui-switch-admin-off-lore"))
                    .glint(on).build(), click -> {
                viewer.closeInventory();
                if (on) {
                    sound(Sound.BLOCK_BEACON_DEACTIVATE);
                    plugin.adminMode().disable(viewer, false);
                } else {
                    sound(Sound.BLOCK_BEACON_ACTIVATE);
                    plugin.adminMode().enable(viewer);
                    // Back into the dashboard, now with the compass in the hotbar.
                    if (plugin.adminMode().active(viewer)) {
                        Bukkit.getScheduler().runTask(plugin, this::reopen);
                    }
                }
            }));
        }
        buttons.add(new Button(Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory()));
        if (viewer.hasPermission(StaffChat.PERM) && plugin.feature(Feature.STAFF_CHAT)) {
            boolean on = plugin.staffChat().toggled(viewer);
            buttons.add(new Button(Items.of(on ? Material.LIME_CANDLE : Material.GRAY_CANDLE)
                    .name(messages.item("gui-switch-staffchat", tag("state", messages.get(on ? "gui-state-on" : "gui-state-off"))))
                    .lore(messages.lore(on ? "gui-switch-staffchat-on-lore" : "gui-switch-staffchat-off-lore"))
                    .glint(on).build(), click -> {
                clickSound();
                plugin.staffChat().toggle(viewer);
                render();
            }));
        }
        return buttons;
    }
}

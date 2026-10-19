package at.marcelr.mradmin.gui;

import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import at.marcelr.mradmin.Settings;
import at.marcelr.mradmin.health.HotChunks;
import at.marcelr.mradmin.mode.AdminModeManager;
import at.marcelr.mradmin.util.Layout;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static at.marcelr.mradmin.Messages.tag;

/**
 * Server health at a glance:
 * <pre>
 *  row 0:  . . [TPS] . [MSPT] . [RAM] . .
 *  row 1:  one item per world (chunks, entities, players), centered
 *  row 2-4: conspicuous objects reported by a plugin (MRchunks: chunk loaders), otherwise the chunks with the most
 *           entities (lag sources) - in columns 1-7, a click opens the object or teleports to the chunk
 *  row 5:  [back] . [close]
 * </pre>
 * The expensive part (counting entities per chunk) is recomputed at most every 5 seconds.
 */
public final class ServerStatusMenu extends Menu {

    public static final String PERM = at.marcelr.mradmin.Perms.STATUS;
    private static final long RECOUNT_MS = 5_000;
    private static final int TOP = 21;

    private final Runnable back;
    private List<HotChunks.Chunk> hot = List.of();
    private long counted;

    public ServerStatusMenu(MRAdmin plugin, Player viewer, Runnable back) {
        super(plugin, viewer, 6, plugin.messages().get("gui-title-status"));
        this.back = back;
    }

    @Override
    protected void draw() {
        Messages messages = plugin.messages();
        double[] tps = Bukkit.getTPS();
        double mspt = Bukkit.getAverageTickTime();
        set(2, Items.of(tps[0] >= 19.5 ? Material.LIME_CONCRETE : tps[0] >= 15 ? Material.YELLOW_CONCRETE : Material.RED_CONCRETE)
                .name(messages.item("gui-status-tps", tag("tps", format(Math.min(20, tps[0])))))
                .lore(messages.lore("gui-status-tps-lore", tag("tps1", format(Math.min(20, tps[0]))),
                        tag("tps5", format(Math.min(20, tps[1]))), tag("tps15", format(Math.min(20, tps[2])))))
                .build());
        set(4, Items.of(mspt <= 40 ? Material.CLOCK : Material.REPEATER)
                .name(messages.item("gui-status-mspt", tag("mspt", format(mspt))))
                .lore(messages.lore("gui-status-mspt-lore")).build());
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1_048_576;
        long max = rt.maxMemory() / 1_048_576;
        set(6, Items.of(Material.REDSTONE).name(messages.item("gui-status-ram", tag("used", used), tag("max", max)))
                .lore(messages.lore("gui-status-ram-lore", tag("percent", max == 0 ? 0 : used * 100 / max))).build());

        List<World> worlds = Bukkit.getWorlds().stream().limit(9).toList();
        int[] worldSlots = Layout.centered(1, worlds.size());
        for (int w = 0; w < worlds.size(); w++) {
            World world = worlds.get(w);
            Material icon = switch (world.getEnvironment()) {
                case NETHER -> Material.NETHERRACK;
                case THE_END -> Material.END_STONE;
                default -> Material.GRASS_BLOCK;
            };
            set(worldSlots[w], Items.of(icon).name(messages.item("gui-status-world", tag("world", world.getName())))
                    .lore(messages.lore("gui-status-world-lore",
                            tag("chunks", world.getLoadedChunks().length),
                            tag("entities", world.getEntityCount()),
                            tag("players", world.getPlayerCount())))
                    .build());
        }

        // A plugin that reports activity (MRchunks: conspicuous chunk loaders) replaces the hot chunks.
        Settings.HubButton provider = activityProvider();
        if (provider != null) {
            drawActivity(provider);
        } else {
            drawHotChunks();
        }

        fillRow(0);
        fillRow(5);
        set(48, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
            clickSound();
            back.run();
        });
        set(50, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());
    }

    /** The first plugin button whose plugin is installed, the viewer may use, and that reports activity. */
    private Settings.HubButton activityProvider() {
        for (Settings.HubButton button : plugin.settings().adminMode().buttons()) {
            Plugin other = button.plugin().isEmpty() ? null : Bukkit.getPluginManager().getPlugin(button.plugin());
            if (other != null && other.isEnabled() && (button.permission().isEmpty() || viewer.hasPermission(button.permission()))
                    && plugin.hooks().hasActivity(button)) {
                return button;
            }
        }
        return null;
    }

    /** Rows 2-4: the plugin's entries (already built in its language), a click opens them there. */
    private void drawActivity(Settings.HubButton provider) {
        List<Map<String, Object>> entries = plugin.hooks().activity(provider, viewer);
        int index = 0;
        for (Map<String, Object> entry : entries) {
            if (index == TOP || !(entry.get("icon") instanceof ItemStack icon) || !(entry.get("id") instanceof String id)) {
                continue;
            }
            int slot = 18 + (index / 7) * 9 + 1 + index % 7;
            index++;
            set(slot, icon, click -> {
                clickSound();
                if (!plugin.hooks().openActivity(provider, viewer, id, () -> new ServerStatusMenu(plugin, viewer, back).open())) {
                    sound(org.bukkit.Sound.ENTITY_VILLAGER_NO);
                }
            });
        }
        if (index == 0) {
            set(31, Items.of(Material.LIME_DYE).name(plugin.messages().item("gui-status-activity-none"))
                    .lore(plugin.messages().lore("gui-status-activity-none-lore", tag("plugin", provider.plugin()))).build());
        }
    }

    /** Rows 2-4: the chunks with the most entities (when no plugin reports activity). */
    private void drawHotChunks() {
        Messages messages = plugin.messages();
        if (System.currentTimeMillis() - counted > RECOUNT_MS) {
            hot = HotChunks.count(TOP);
            counted = System.currentTimeMillis();
        }
        boolean teleport = viewer.hasPermission(AdminModeManager.PERM_TELEPORT) && plugin.feature(at.marcelr.mradmin.Feature.TELEPORT);
        // 3 rows of 7 (columns 1-7), so the block is centered.
        int index = 0;
        for (HotChunks.Chunk chunk : hot) {
            int slot = 18 + (index / 7) * 9 + 1 + index % 7;
            index++;
            String types = chunk.types().entrySet().stream()
                    .sorted(Map.Entry.<EntityType, Integer>comparingByValue().reversed())
                    .limit(3)
                    .map(e -> e.getValue() + "x " + e.getKey().getKey().getKey())
                    .collect(Collectors.joining(", "));
            Items item = Items.of(chunk.count() >= 200 ? Material.MAGMA_BLOCK : Material.HOPPER)
                    .name(messages.item("gui-status-chunk", tag("count", chunk.count())))
                    .lore(messages.lore("gui-status-chunk-lore", tag("world", chunk.world().getName()),
                            tag("x", chunk.x() * 16 + 8), tag("z", chunk.z() * 16 + 8), tag("types", types)));
            if (teleport) {
                item.lore(messages.lore("gui-status-chunk-click"));
            }
            set(slot, item.build(), click -> {
                if (allowed(AdminModeManager.PERM_TELEPORT)) {
                    viewer.closeInventory();
                    int bx = chunk.x() * 16 + 8;
                    int bz = chunk.z() * 16 + 8;
                    Location to = new Location(chunk.world(), bx + 0.5, chunk.world().getHighestBlockYAt(bx, bz) + 1, bz + 0.5);
                    viewer.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
            });
        }
        if (hot.isEmpty()) {
            set(31, Items.of(Material.LIME_DYE).name(messages.item("gui-status-no-chunks")).build());
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}

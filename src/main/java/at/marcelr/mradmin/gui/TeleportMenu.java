package at.marcelr.mradmin.gui;

import at.marcelr.mradmin.Feature;
import at.marcelr.mradmin.MRAdmin;
import at.marcelr.mradmin.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Comparator;
import java.util.List;

import static at.marcelr.mradmin.Messages.tag;

/** All online players: a click opens the player's action menu, shift-click teleports right away. */
public final class TeleportMenu extends Menu {

    private static final int PER_PAGE = 45;
    private int page;

    public TeleportMenu(MRAdmin plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.messages().get("gui-title-teleport"));
    }

    @Override
    protected void draw() {
        Messages messages = plugin.messages();
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(viewer))
                .filter(viewer::canSee)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> (Player) p)
                .toList();
        int pages = Pages.count(players.size(), PER_PAGE);
        page = Math.min(page, pages - 1);

        if (players.isEmpty()) {
            set(22, Items.of(Material.BARRIER).name(messages.item("gui-teleport-empty")).build());
        }
        int slot = 0;
        for (Player target : Pages.slice(players, page, PER_PAGE)) {
            Location l = target.getLocation();
            set(slot++, Items.head(target)
                    .name(messages.item("gui-teleport-player-name", tag("player", target.getName())))
                    .lore(messages.lore("gui-teleport-player-lore",
                            tag("world", l.getWorld().getName()),
                            tag("x", l.getBlockX()), tag("y", l.getBlockY()), tag("z", l.getBlockZ()),
                            tag("gamemode", target.getGameMode().name().toLowerCase(java.util.Locale.ROOT)),
                            tag("health", Math.round(target.getHealth())),
                            tag("ping", target.getPing())))
                    .lore(plugin.feature(Feature.TELEPORT) && viewer.hasPermission(at.marcelr.mradmin.Perms.PLAYER_TELEPORT) ? List.of(messages.item("gui-teleport-player-shift")) : List.of())
                    .build(), click -> {
                if (click.isShiftClick() && plugin.feature(Feature.TELEPORT)
                        && viewer.hasPermission(at.marcelr.mradmin.Perms.PLAYER_TELEPORT)) {
                    teleport(target);
                } else {
                    clickSound();
                    new PlayerActionMenu(plugin, viewer, target, () -> new TeleportMenu(plugin, viewer).open()).open();
                }
            });
        }

        fillRow(5);
        Pages.navigation(this, messages, page, pages, p -> page = p);
        set(48, Items.of(Material.ARROW).name(messages.item("gui-back")).build(), click -> {
            clickSound();
            new HubMenu(plugin, viewer).open();
        });
        set(50, Items.of(Material.BARRIER).name(messages.item("gui-close")).build(), click -> viewer.closeInventory());
    }

    private void teleport(Player target) {
        if (!target.isOnline()) {
            sound(Sound.ENTITY_VILLAGER_NO);
            render();
            return;
        }
        viewer.closeInventory();
        viewer.teleport(target.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
        plugin.messages().send(viewer, "mode-teleported", tag("player", target.getName()));
        sound(Sound.ENTITY_ENDERMAN_TELEPORT);
    }
}

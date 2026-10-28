package at.marcelr.mradmin.player;

import at.marcelr.mradmin.MRAdmin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Players that are no real players: NPCs of the modules (e.g. MRchunks' dummy players, see
 * {@link at.marcelr.mradmin.api.AdminModule#isNpc}) and of other plugins (Citizens and others mark them with the
 * metadata "NPC"). The server counts them as online players; MRadmin leaves them out of player lists, counts,
 * maintenance kicks and the like.
 */
public final class Npcs {

    private Npcs() {
    }

    public static boolean is(Player player) {
        if (player.hasMetadata("NPC")) {
            return true;
        }
        MRAdmin plugin = JavaPlugin.getPlugin(MRAdmin.class);
        return plugin.modules() != null && plugin.modules().npc(player);
    }

    /** Online players without NPCs. */
    public static List<Player> online() {
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!is(player)) {
                players.add(player);
            }
        }
        return players;
    }
}

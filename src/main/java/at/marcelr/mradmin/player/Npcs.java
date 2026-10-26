package at.marcelr.mradmin.player;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Players that are no real players: MRchunks' dummy players (they sit on chunk loaders) and NPCs of other plugins
 * (Citizens and others mark them with the metadata "NPC"). The server counts them as online players; MRadmin leaves
 * them out of player lists, counts, maintenance kicks and the like.
 */
public final class Npcs {

    /** MRchunks marks its dummies with this key in the player's data container. */
    private static final NamespacedKey MRCHUNKS_DUMMY = new NamespacedKey("mrchunks", "dummy");

    private Npcs() {
    }

    public static boolean is(Player player) {
        return player.hasMetadata("NPC") || player.getPersistentDataContainer().has(MRCHUNKS_DUMMY);
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

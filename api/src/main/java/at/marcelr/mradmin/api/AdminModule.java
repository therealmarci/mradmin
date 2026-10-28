package at.marcelr.mradmin.api;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a plugin offers MRadmin. Everything except {@link #id()}, {@link #plugin()} and {@link #viewPermission()} is
 * optional - a module offers only what it has. MRadmin calls these on the main thread and checks
 * {@link #viewPermission()} before it shows anything of the module; anything finer (e.g. who may delete) the module
 * checks itself, or for the web it declares the permissions in {@link WebModule}.
 * <p>
 * Texts are built by the module in its own language files, for the viewer.
 */
public interface AdminModule {

    /** Short, stable, lowercase id, e.g. "mrchunks" - used in config, web addresses and permissions keys. */
    String id();

    Plugin plugin();

    /** Permission to see the module in MRadmin at all (dashboard button, player info, web pages). */
    String viewPermission();

    /** Icon of the dashboard button. */
    default Material icon() {
        return Material.BOOK;
    }

    // ------------------------------------------------------------ in game

    /** Live lines on the module's dashboard button, e.g. "12 chunk loaders (8 active)". */
    default List<Component> summary(Player viewer) {
        return List.of();
    }

    /**
     * Opens the module's own admin menu ({@code back} returns to MRadmin). Without one, the dashboard button runs the
     * command configured for it.
     * @return false if the viewer may not
     */
    default boolean openMenu(Player viewer, Runnable back) {
        return false;
    }

    /** Lines about one player in MRadmin's player menu. */
    default List<Component> playerSummary(Player viewer, UUID player) {
        return List.of();
    }

    /** Opens the module's menu for one player. @return false if there is none or the viewer may not */
    default boolean openPlayerMenu(Player viewer, UUID player, Runnable back) {
        return false;
    }

    /** Conspicuous things for MRadmin's server health (e.g. chunk loaders that cause lag), worst first. */
    default List<HealthEntry> health(Player viewer) {
        return List.of();
    }

    /** Opens what a health entry (or a ticket from {@link MRadminService#report}) stands for. */
    default boolean openHealth(Player viewer, String id, Runnable back) {
        return false;
    }

    // ------------------------------------------- shared with other modules

    /**
     * Whether the module has the player in a state where other plugins must leave them (and their inventory) alone -
     * e.g. MRpvp during a duel: MRsleep leaves them out of the quota, MRtoolbar pauses. Asked through
     * {@link MRadminService#busy}; cheap, it may be asked often.
     */
    default boolean busy(Player player) {
        return false;
    }

    /**
     * Whether the world belongs to the module - e.g. MRpvp's arena world: no chunk loaders, no sleep quota there.
     * Asked through {@link MRadminService#worldOwner}.
     */
    default boolean ownsWorld(World world) {
        return false;
    }

    /**
     * Items the module keeps for an online player outside the normal inventory (e.g. MRtoolbar's tool belts), for
     * modules that save a player's whole state (e.g. MRpvp before a duel). One entry per group: "items" (List of
     * byte[] from {@code ItemStack.serializeAsBytes}, null for an empty place) plus the module's own keys (MRtoolbar:
     * "slot", "selected"). Asked through {@link MRadminService#storedItems}.
     */
    default List<Map<String, Object>> storedItems(Player player) {
        return List.of();
    }

    /**
     * Whether the player is no real player but one of the module's NPCs (e.g. MRchunks' dummy players). MRadmin leaves
     * them out of player lists, counts and maintenance kicks. Cheap, asked often. Since API 3.
     */
    default boolean isNpc(Player player) {
        return false;
    }

    /**
     * Chunks the module keeps loaded itself (e.g. MRchunks' chunk loaders), shown in the server health; -1 = none.
     * Main thread, asked every second - keep it cheap. Since API 3.
     */
    default int loadedChunks() {
        return -1;
    }

    // ---------------------------------------------------------------- web

    /**
     * Data and actions for MRadmin's web interface, or null if the module has none. Always the same object - like
     * {@link #id()} and {@link #viewPermission()} it may be read from any thread.
     */
    default @Nullable WebModule web() {
        return null;
    }
}

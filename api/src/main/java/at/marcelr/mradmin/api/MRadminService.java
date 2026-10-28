package at.marcelr.mradmin.api;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * MRadmin for other plugins: register an {@link AdminModule} and use MRadmin's services. MRadmin offers it through
 * Bukkit's services manager while it runs; {@link #get()} returns it.
 * <p>
 * <b>Optional for both sides.</b> A plugin compiles against this API ({@code compileOnly}), lists MRadmin under
 * {@code softdepend} and only touches these classes while MRadmin is enabled - keep everything that uses them in one
 * class that is loaded only then. Without MRadmin the plugin simply has no admin integration; MRadmin works without
 * any module.
 * <p>
 * Main thread only. Versions: {@link #API_VERSION} of the API a plugin was built with, {@link #apiVersion()} of the
 * running MRadmin. New methods only come with a higher version and as default methods, so a module built against an
 * older API keeps working; a plugin that uses newer methods checks {@code apiVersion()} first.
 */
public interface MRadminService {

    /** Version of this API (2: {@link #items()}). */
    int API_VERSION = 2;

    /** The running MRadmin, or null if it is not enabled. Only call while the MRadmin plugin is installed. */
    static @Nullable MRadminService get() {
        return Bukkit.getServicesManager().load(MRadminService.class);
    }

    /** API version of the running MRadmin. */
    int apiVersion();

    /**
     * Registers the module (replaces an earlier one with the same id). Modules are removed automatically when their
     * plugin is disabled.
     */
    void register(AdminModule module);

    void unregister(String moduleId);

    /** Whether the player is in MRadmin's admin mode right now (e.g. to leave them out of a sleep quota). */
    boolean adminMode(Player player);

    /**
     * The module that has the player busy right now (see {@link AdminModule#busy}), e.g. "mrpvp" during a duel - leave
     * the player and their inventory alone. Null if none.
     */
    @Nullable String busy(Player player);

    /** The module the world belongs to (see {@link AdminModule#ownsWorld}), e.g. "mrpvp" for its arena world, or null. */
    @Nullable String worldOwner(World world);

    /** Items modules keep for the player outside the inventory (see {@link AdminModule#storedItems}), by module id. */
    Map<String, List<Map<String, Object>>> storedItems(Player player);

    /**
     * Something of the module changed - open pages of the web interface load it again. Carries no content, only the
     * type and an id (e.g. "game", "1042"); only members who may see the module get it.
     */
    void webChanged(String moduleId, String type, String id);

    /** A line in MRadmin's Discord admin log (event "plugin-alert"). */
    void adminLog(String moduleId, String text);

    /**
     * Reports a problem of a player that the team should look at: MRadmin opens a ticket (if tickets are on), or adds
     * the text to the ticket already open for the same {@link Report#key()}.
     */
    void report(String moduleId, Report report);

    /** The problem behind {@code key} is solved - MRadmin adds the note to its open ticket (if there is one). */
    void resolve(String moduleId, String key, String note);

    /**
     * Items as data for the web interface and back (API version 2). Null with an older MRadmin - check
     * {@link #apiVersion()} first, the method is missing there.
     */
    default @Nullable WebItems items() {
        return null;
    }
}

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

    /**
     * Version of this API (2: {@link #items()}; 3: {@link AdminModule#isNpc}, {@link AdminModule#loadedChunks},
     * {@link WebModule#tile}, {@link WebModule#pushKinds}, {@link WebModule#reportCategories}; 4: the player area -
     * {@link WebModule#selfAreas}, {@link WebModule#selfAction}, {@link WebModule#selfPermissions},
     * {@link WebModule#rankings}; 5: more building blocks for it - {@link SelfBlock.Cards}, {@link SelfBlock.Meters},
     * {@link SelfBlock.People}, {@link SelfBlock.Form}, {@link SelfBlock.Embed}, {@link SelfBlock.Group}; 6: module
     * history - {@link #moduleHistoryAdd}, {@link #moduleHistory}; 7: the web map - {@link AdminModule#mapLayers},
     * {@link AdminModule#mapIcons}, {@link #mapChanged}, {@link #mapUrl()}, {@link #mapUrl(World, int, int)}; 8: the map of
     * the player's page - {@link WebModule#selfMap}, {@link WebModule#selfMapAction}, {@link SelfMapLayer}; 9: groups,
     * selections and symbols there - {@link SelfMapLayer#group}, {@link SelfMapLayer.Action#choice},
     * {@link SelfMapLayer.Action#icon}; 10: heights of markers - {@link SelfMapLayer.Marker#y}; 11: a card on a marker -
     * {@link SelfMapLayer.Marker#card}, {@link SelfMapLayer.Marker#area}; 12: the config in the web interface -
     * {@link WebModule#webConfig}, {@link WebConfig}; 13: the log - {@link #log}).
     */
    int API_VERSION = 13;

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

    /**
     * A line in MRadmin's Discord admin log (event "plugin-alert"). Only Discord: the log of the web interface gets
     * its entry from {@link #log} (API 13), so a module that does both does not appear twice.
     */
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

    /**
     * Saves one entry of a module's own measurements with MRadmin's data (files or database, whatever
     * {@code storage.type} says), written in the background (API version 6). Entries older than {@code keepMillis}
     * are removed from time to time. Does nothing with an older MRadmin.
     *
     * @param data one line of text in the module's own format
     */
    default void moduleHistoryAdd(String moduleId, long time, String data, long keepMillis) {
    }

    /**
     * The saved entries since {@code since}, oldest first: time -> data (API version 6). Blocking, so only while the
     * plugin enables. Empty with an older MRadmin.
     */
    default Map<Long, String> moduleHistory(String moduleId, long since) {
        return Map.of();
    }

    // ------------------------------------------------- web map (since API 7)

    /**
     * Something on the module's map layers changed. Without effect after MRadmin 1.1 - the map of the player page
     * loads them again by itself; kept so modules built for API 7 still load.
     */
    default void mapChanged(String moduleId) {
    }

    /**
     * Link to the map of the player page (web.public-url + /me/map), or null if there is none (no squaremap, no
     * address, no player page). With {@link #mapUrl(World, int, int)} centred on a place.
     */
    default @Nullable String mapUrl() {
        return null;
    }

    /** Link to the map of the player page centred on a place, or null if there is none or the world is not on it. */
    default @Nullable String mapUrl(World world, int x, int z) {
        return null;
    }

    // ------------------------------------------------------------------ log (since API 13)

    /**
     * Writes an event into MRadmin's log (web interface, page "Log", kept up to 14 days) - the same events a plugin sends
     * to its Discord log, whether Discord is switched on or not.
     *
     * @param event key of the event, e.g. "loader-placed" (filterable)
     * @param text  the finished text in the server language (Discord markdown like **bold** is shown)
     * @param color colour of the event (0xRRGGBB), e.g. the Discord colour
     */
    default void log(String moduleId, String event, String text, int color) {
    }
}

package at.marcelr.mradmin.api;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The web part of a module. MRadmin serves it under {@code /api/modules/<id>/...}:
 * <pre>
 *  GET  /api/modules/&lt;id&gt;/data/&lt;request&gt;?arg=value     {@link #data}
 *  POST /api/modules/&lt;id&gt;/actions/&lt;action&gt;  {args}     {@link #action}
 *  GET  /api/players/&lt;uuid&gt;/modules/&lt;id&gt;                 {@link #player}
 *  POST /api/players/&lt;uuid&gt;/modules/&lt;id&gt;/&lt;action&gt;      {@link #playerAction}
 * </pre>
 * Permissions are checked by MRadmin before it calls the module: every request needs the module's view permission,
 * plus the permission the module names for it in {@link #permissions()} (keys) and {@link #requests()} /
 * {@link #actions()} / {@link #playerActions()} (request or action -> key). The web interface gets the keys the member
 * has, e.g. {@code may.mrchunksManage} for key "manage" of module "mrchunks".
 * <p>
 * Everything is plain data: maps, lists, strings, numbers, booleans - texts already in the module's language.
 * {@link #data}, {@link #action}, {@link #player}, {@link #playerAction} and {@link #flags} run on the main thread.
 * The tables ({@link #permissions()}, {@link #requests()}, {@link #actions()}, {@link #playerActions()}) must be fixed:
 * MRadmin reads them from any thread to check permissions (LuckPerms must not be asked on the main thread).
 */
public interface WebModule {

    /** Permission keys -> permissions, e.g. "manage" -> "mrchunks.admin.manage". "view" is always the view permission. */
    Map<String, String> permissions();

    /** Data requests that need more than viewing: request -> permission key. Others only need "view". */
    default Map<String, String> requests() {
        return Map.of();
    }

    /**
     * Answers a data request (e.g. "overview", "loaders", "loader" with arg "id").
     * @return the data, or null if there is no such request or object (the web answers 404)
     */
    @Nullable Object data(String request, Map<String, String> args);

    /** Actions: action -> permission key (an action not listed here is refused). */
    Map<String, String> actions();

    /**
     * Runs an action, e.g. "toggle" with arg "id". The module validates the arguments itself.
     * @return null if done, otherwise an error key the web interface shows (e.g. "not-found", "loader-locked")
     */
    @Nullable String action(String action, Map<String, Object> args, WebUser user);

    /** The module's part of a player's page, or null if it has none for this player. */
    default @Nullable Object player(UUID player) {
        return null;
    }

    /** Actions on a player: action -> permission key. */
    default Map<String, String> playerActions() {
        return Map.of();
    }

    /** Runs an action on a player. @return null if done, otherwise an error key */
    default @Nullable String playerAction(UUID player, String action, Map<String, Object> args, WebUser user) {
        return "unknown-action";
    }

    /** Hints for the start page of the web interface (e.g. locked chunk loaders), worst first. Main thread. */
    default List<WebAlert> alerts() {
        return List.of();
    }

    /** Marks for MRadmin's player list: player -> keys (e.g. "chunks-locked"), translated by the web interface. */
    default Map<UUID, List<String>> flags() {
        return Map.of();
    }

    /**
     * The module's tile on the start page of the web interface (e.g. MRchunks: active loaders, locks), or null for
     * none. Only members who may see the module get it. Main thread, asked every few seconds - keep it cheap.
     * Since API 3.
     */
    default @Nullable WebTile tile() {
        return null;
    }

    /**
     * Kinds of web push notifications the module sends through {@link Report#push()} (e.g. "chunks-locked"). Members
     * who may see the module can switch them on and off. Asked from any thread - return a fixed list. Since API 3.
     */
    default List<WebPushKind> pushKinds() {
        return List.of();
    }

    /**
     * Names of the ticket categories of the module's reports ({@link Report#kind()} -> name in the module's language),
     * shown in tickets and as a filter. Asked from any thread - return fixed names. Since API 3.
     */
    default Map<String, String> reportCategories() {
        return Map.of();
    }

    // ------------------------------------------------- player area (since API 4)

    /**
     * Permissions the module wants to know of a player for their own page (e.g. "mrchunks.use"). MRadmin checks them
     * beforehand - also while the player is offline - and passes the granted ones in {@link SelfViewer#permissions()}.
     * Asked from any thread - return a fixed set.
     */
    default java.util.Set<String> selfPermissions() {
        return java.util.Set.of();
    }

    /**
     * The module's areas on the player's own page (player area, /web), built from {@link SelfBlock}s - only what
     * belongs to {@code viewer.player()}. Empty = the module shows nothing there (e.g. no permission). Main thread.
     */
    default List<SelfArea> selfAreas(SelfViewer viewer) {
        return List.of();
    }

    /**
     * A button of the player's own page was clicked ({@link SelfBlock.Button}): {@code area} and {@code action} as
     * given there, {@code args} its args (plus "value" from an input). Check everything again - only the player's
     * own things, the same rules and permissions as in game. Main thread.
     * @return null if done, otherwise the reason in the viewer's language (shown to the player)
     */
    default @Nullable String selfAction(SelfViewer viewer, String area, String action, Map<String, String> args) {
        return "?";
    }
}

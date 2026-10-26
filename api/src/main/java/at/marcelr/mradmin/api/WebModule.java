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
}

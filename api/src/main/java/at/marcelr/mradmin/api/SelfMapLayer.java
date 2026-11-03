package at.marcelr.mradmin.api;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One layer of a module on the map of the player's own page (tab "Map", see {@link WebModule#selfMap}) - only what this
 * player may see and do. MRadmin draws it over the map pictures squaremap renders. All texts in the viewer's language.
 * Since API 8.
 *
 * @param id      stable id within the module, e.g. "shops" - the viewer remembers whether it is switched on
 * @param name    name in the layer list, e.g. "Shops"
 * @param color   "#RRGGBB" for the layer list and markers without an own colour
 * @param markers everything of the layer, in all worlds (MRadmin shows those of the chosen world)
 * @param here    actions for a right-click on a free spot of the map, e.g. "Create waypoint here" - they get the
 *                spot as args "world", "x", "z"
 * @param toggle  an extra switch in the layer list (e.g. "shown in Xaero"), or null
 */
public record SelfMapLayer(String id, String name, String color, List<Marker> markers, List<Action> here,
                           @Nullable Toggle toggle) {

    /**
     * A point ({@code width} and {@code length} 0) or an area from (x, z) on {@code width} × {@code length} blocks.
     *
     * @param world   key of the world, e.g. "minecraft:overworld"
     * @param color   "#RRGGBB", or "" for the layer's colour
     * @param details lines shown when the marker is clicked (plain text)
     * @param actions in its right-click menu (and below the details) - they get the marker's id as arg "marker"
     */
    public record Marker(String id, String world, int x, int z, int width, int length, String label, String color,
                         List<String> details, List<Action> actions) {
    }

    /**
     * An entry of a right-click menu: sends {@code id} to {@link WebModule#selfMapAction}. With {@code confirm} the
     * player confirms that question first; with {@code input} they type a text first (arg "value").
     *
     * @param tone "", "good" or "bad" (e.g. delete)
     */
    public record Action(String id, String label, String tone, @Nullable String confirm,
                         SelfBlock.@Nullable Input input) {
    }

    /** A switch in the layer list: a click sends {@code action} (with arg "on" = the new state). */
    public record Toggle(String label, boolean on, String action) {
    }
}

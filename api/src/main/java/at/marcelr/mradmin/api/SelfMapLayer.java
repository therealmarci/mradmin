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
 * @param group   heading the layer stands under in the layer list, e.g. "Chunk loaders" - layers with the same group
 *                stand together; "" = the module's name (since API 9, older MRadmin ignores it)
 */
public record SelfMapLayer(String id, String name, String color, List<Marker> markers, List<Action> here,
                           @Nullable Toggle toggle, String group) {

    /** A layer under the module's name (as in API 8). */
    public SelfMapLayer(String id, String name, String color, List<Marker> markers, List<Action> here,
                        @Nullable Toggle toggle) {
        this(id, name, color, markers, here, toggle, "");
    }

    /**
     * A point ({@code width} and {@code length} 0) or an area from (x, z) on {@code width} × {@code length} blocks.
     *
     * @param world   key of the world, e.g. "minecraft:overworld"
     * @param color   "#RRGGBB", or "" for the layer's colour
     * @param details lines shown when the marker is clicked (plain text)
     * @param actions in its right-click menu (and below the details) - they get the marker's id as arg "marker"
     * @param y       its height, or null if it has none: in the Nether MRadmin then shows it on the ceiling map
     *                (from map.nether-roof.from-y) or below - without a height on both (since API 10)
     */
    public record Marker(String id, String world, int x, int z, int width, int length, String label, String color,
                         List<String> details, List<Action> actions, @Nullable Integer y) {

        /** A marker without a height (as in API 8 and 9). */
        public Marker(String id, String world, int x, int z, int width, int length, String label, String color,
                      List<String> details, List<Action> actions) {
            this(id, world, x, z, width, length, label, color, details, actions, null);
        }
    }

    /**
     * An entry of a right-click menu: sends {@code id} to {@link WebModule#selfMapAction}. With {@code confirm} the
     * player confirms that question first; with {@code input} and/or {@code choice} they fill in one dialog first
     * (args "value" and "choice").
     *
     * @param tone   "", "good" or "bad" (e.g. delete - shown in red, at the end of the menu)
     * @param choice a selection in the same dialog as the input, e.g. the category (since API 9), or null
     * @param icon   a symbol in front of the label: "plus", "edit", "trash", "power", "compass", "eye" or "" (since API 9)
     */
    public record Action(String id, String label, String tone, @Nullable String confirm,
                         SelfBlock.@Nullable Input input, @Nullable Choice choice, String icon) {

        /** An action without selection and symbol (as in API 8). */
        public Action(String id, String label, String tone, @Nullable String confirm, SelfBlock.@Nullable Input input) {
            this(id, label, tone, confirm, input, null, "");
        }
    }

    /**
     * A selection in the dialog of an {@link Action}: one of {@code options}, preset to {@code value} (or the first).
     * Since API 9.
     */
    public record Choice(String label, String value, List<Option> options) {
    }

    /** An entry of a {@link Choice}; {@code color} "#RRGGBB" or "" for a dot in front of it. */
    public record Option(String id, String label, String color) {
    }

    /** A switch in the layer list: a click sends {@code action} (with arg "on" = the new state). */
    public record Toggle(String label, boolean on, String action) {
    }
}

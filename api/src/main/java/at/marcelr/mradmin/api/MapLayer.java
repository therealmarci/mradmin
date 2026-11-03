package at.marcelr.mradmin.api;

import java.util.List;

/**
 * One layer of a module on the web map, see {@link AdminModule#mapLayers()}. MRadmin draws it on squaremap (if
 * installed) in every world the map shows; visitors switch it on and off in the layer menu. The server owner can
 * switch a layer off or hide it by default in MRadmin's config ({@code map.layers}, as "&lt;module&gt;:&lt;id&gt;").
 * Since API 7.
 *
 * @param id            short, stable, lowercase id within the module, e.g. "active"
 * @param name          name in the layer menu, in the server language (the map has no single viewer)
 * @param defaultHidden hidden when the map opens
 * @param priority      order in the layer menu and on top of each other: lower first and on top
 * @param markers       everything of the layer, in all worlds
 */
public record MapLayer(String id, String name, boolean defaultHidden, int priority, List<MapMarker> markers) {
}

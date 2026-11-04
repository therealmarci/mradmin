package at.marcelr.mradmin.api;

import java.util.UUID;

/**
 * Something drawn on a {@link MapLayer}: an area or an icon. Coordinates are block coordinates of the world
 * {@code world} (its UUID). The tooltips are HTML - the module escapes its texts itself (player names, loader names
 * ...); empty = none. Since API 7.
 */
public sealed interface MapMarker {

    /** Unique within the layer, e.g. "loader_12". */
    String id();

    UUID world();

    /**
     * A rectangle from (minX, minZ) to (maxX, maxZ), max exclusive - a chunk area of chunks 0..1 is 0, 0, 32, 32.
     *
     * @param hover tooltip while the mouse is over it
     * @param click tooltip on click
     */
    record Area(String id, UUID world, int minX, int minZ, int maxX, int maxZ, Style style, String hover, String click)
            implements MapMarker {
    }

    /**
     * An icon centred on (x, z).
     *
     * @param icon key of one of the module's icons ({@link AdminModule#mapIcons()})
     * @param size width and height in pixels
     */
    record Icon(String id, UUID world, double x, double z, String icon, int size, String hover, String click)
            implements MapMarker {
    }

    /**
     * Look of an area: colours as 0xRRGGBB, opacities 0.0 - 1.0, line width in pixels.
     */
    record Style(int strokeColor, double strokeOpacity, int strokeWeight, int fillColor, double fillOpacity) {
    }
}

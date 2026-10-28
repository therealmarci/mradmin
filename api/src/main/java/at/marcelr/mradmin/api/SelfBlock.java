package at.marcelr.mradmin.api;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The building blocks of a module's area on a player's own page ({@link SelfArea}). The module only describes what to
 * show; MRadmin draws it - the same look for every plugin, and a new feature of a plugin needs no change in MRadmin.
 * Texts are plain text in the viewer's language. {@code tone} is "", "good", "info", "warn" or "bad". Since API 4.
 */
public sealed interface SelfBlock {

    /** A row of key figures, e.g. "Budget 98 / 147". */
    record Stats(List<Stat> stats) implements SelfBlock {
    }

    record Stat(String label, String value, String tone) {
    }

    /** A paragraph, e.g. a hint. */
    record Text(String text, String tone) implements SelfBlock {
    }

    /**
     * A list of things (e.g. the loaders), each row with its cells and its own buttons.
     * @param empty shown when there are no rows
     */
    record Table(String title, List<String> columns, List<Row> rows, String empty) implements SelfBlock {
    }

    record Row(List<String> cells, String tone, List<Button> buttons) {
    }

    /** Buttons on their own, e.g. "Switch all off". */
    record Buttons(List<Button> buttons) implements SelfBlock {
    }

    /**
     * A button: a click sends {@code action} with {@code args} to {@link WebModule#selfAction}. With {@code confirm}
     * the player has to confirm that question first; with {@code input} they type a text first, sent as
     * {@code args.value}.
     */
    record Button(String label, String action, Map<String, String> args, String tone, @Nullable String confirm,
                  @Nullable Input input) {
    }

    /** A text field of a button (e.g. a new name). */
    record Input(String label, String value, int maxLength) {
    }

    /** A ranking, the player's own place highlighted. */
    record Ranking(String title, List<Place> places, String empty) implements SelfBlock {
    }

    record Place(int rank, UUID player, String name, String value, boolean self) {
    }

    /**
     * A small map of areas in block coordinates (e.g. chunk loaders), one per world shown separately.
     * @param link      a bigger map to open (e.g. squaremap), or null
     */
    record AreaMap(String title, List<Rect> rects, @Nullable String link, String linkLabel) implements SelfBlock {
    }

    /** An area on the map: from minX/minZ (inclusive) to maxX/maxZ (exclusive), colour "#RRGGBB". */
    record Rect(String world, int minX, int minZ, int maxX, int maxZ, String label, String color,
                @Nullable String link) {
    }
}

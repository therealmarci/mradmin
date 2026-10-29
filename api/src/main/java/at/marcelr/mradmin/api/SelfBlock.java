package at.marcelr.mradmin.api;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The building blocks of a module's area on a player's own page ({@link SelfArea}). The module only describes what to
 * show; MRadmin draws it - the same look for every plugin, and a new feature of a plugin needs no change in MRadmin.
 * Texts are plain text in the viewer's language. {@code tone} is "", "good", "info", "warn" or "bad"; for comparing
 * the viewer with someone else also "own" (violet) and "other" (amber), e.g. the bars of both sides of a duel (an
 * older MRadmin shows those neutral). Since API 4.
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

    // ------------------------------------------------------------------ since API 5
    // Only send these if MRadminService#apiVersion() is at least 5 - an older MRadmin cannot draw them.

    /**
     * A bar, e.g. an activity against its limit.
     * @param value text next to the label, e.g. "12.4/s"
     * @param fill  how full the bar is, 0..1
     * @param mark  a line on the bar, 0..1 (e.g. where the warning starts), or -1 for none
     * @param hint  small text under the bar, e.g. "warning from 20 · lock from 40" ("" = none)
     */
    record Meter(String label, String value, double fill, double mark, String hint, String tone) {
    }

    /** Bars under a title, e.g. the limits of a loader. {@code text}: a line above them ("" = none). Since API 5. */
    record Meters(String title, String text, List<Meter> meters) implements SelfBlock {
    }

    /**
     * Things as cards side by side (e.g. the loaders). A click on a card opens it with its buttons and details.
     * @param empty shown when there are no cards
     */
    record Cards(String title, List<Card> cards, String empty) implements SelfBlock {
    }

    /**
     * One card.
     * @param id       stable id - an open card stays open when the page reloads after an action
     * @param subtitle e.g. the place ("" = none)
     * @param badge    short status on the card, e.g. "on" or "locked", coloured by {@code tone} ("" = none)
     * @param stats    key figures on the card
     * @param meters   bars on the card (keep it to one or two - the rest belongs into the details)
     * @param primary  the one button right on the card (e.g. on/off), or null
     * @param buttons  shown when the card is open
     * @param details  shown when the card is open (Meters, Text, Embed ...)
     */
    record Card(String id, String title, String subtitle, String badge, String tone, List<Stat> stats,
                List<Meter> meters, @Nullable Button primary, List<Button> buttons, List<SelfBlock> details) {
    }

    /** Players with their heads, each with their own buttons (e.g. invited players). Since API 5. */
    record People(String title, String text, List<Person> people, String empty) implements SelfBlock {
    }

    /**
     * @param detail e.g. "all loaders · until 12.11." ("" = none)
     * @param badge  short label next to the name ("" = none), coloured by {@code tone}
     */
    record Person(UUID player, String name, String detail, String badge, String tone, List<Button> buttons) {
    }

    /**
     * A small form: sending it calls {@code action} with {@code args} plus every field as key -> value
     * ({@link WebModule#selfAction}). Since API 5.
     */
    record Form(String title, String text, List<Field> fields, String submit, String action,
                Map<String, String> args) implements SelfBlock {
    }

    /**
     * A field of a form: {@code type} "text" (up to {@code maxLength} characters) or "select" (one of {@code options},
     * {@code value} preselected).
     */
    record Field(String key, String label, String type, String value, List<Option> options, int maxLength) {
    }

    record Option(String value, String label) {
    }

    /**
     * Another web page shown in a frame, e.g. a live map (only http(s)). If the browser cannot show it (e.g. an http
     * page inside an https panel), the link below still opens it. Since API 5.
     * @param height in pixels (200..900)
     */
    record Embed(String title, String url, int height, String linkLabel) implements SelfBlock {
    }

    /**
     * Several blocks on one tile that belong together, e.g. "invite a player" above the list of invited players.
     * Inside an opened card it is a framed box. Since API 5 (api-1.4.1).
     */
    record Group(String title, String text, List<SelfBlock> blocks) implements SelfBlock {
    }
}

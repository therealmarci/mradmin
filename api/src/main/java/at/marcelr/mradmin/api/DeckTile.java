package at.marcelr.mradmin.api;

import org.jetbrains.annotations.Nullable;

/**
 * A key a module offers for the Stream Deck plugin of MRadmin (see {@link WebModule#deckTiles}). Since API 17.
 * <p>
 * The Stream Deck shows {@code title} small at the top, {@code value} big in the middle, {@code detail} below and the
 * tone as a stripe at the bottom. Keep the texts short - a key is 144 pixels wide.
 *
 * @param id       unique within the module and stable (the key remembers it), e.g. "loader:4" or "last-duel"
 * @param kind     what sort of key it is, for the choice in the Stream Deck (e.g. "chunkloader", "duel", "review");
 *                 lower case letters, digits and "-"
 * @param title    short heading in the viewer's language
 * @param value    the big value, e.g. "On", "Win", "3"
 * @param detail   a short line under the value (may be empty)
 * @param tone     "good", "warn", "bad", "off" (grey) or "info" (accent colour)
 * @param link     page of the web interface to open, as a path ("/game/mrchunks/loaders") or null
 * @param pressable whether pressing the key calls {@link WebModule#deckPress} (otherwise it opens {@code link})
 * @param confirm  whether a press must be confirmed with a second press (for things that cannot be undone easily)
 */
public record DeckTile(String id, String kind, String title, String value, String detail, String tone,
                       @Nullable String link, boolean pressable, boolean confirm) {

    public DeckTile {
        detail = detail == null ? "" : detail;
        tone = tone == null ? "info" : tone;
    }

    /** A key that only shows something and opens {@code link} when pressed. */
    public static DeckTile show(String id, String kind, String title, String value, String detail, String tone,
                                @Nullable String link) {
        return new DeckTile(id, kind, title, value, detail, tone, link, false, false);
    }
}

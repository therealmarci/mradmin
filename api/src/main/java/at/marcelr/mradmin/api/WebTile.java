package at.marcelr.mradmin.api;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A module's tile on the start page of MRadmin's web interface, see {@link WebModule#tile()}. All texts already in the
 * module's language. Since API 3.
 *
 * @param label    title, e.g. "Chunk loaders"
 * @param value    the big number or word, e.g. "12"
 * @param unit     small text after it, e.g. "/ 15 active", or ""
 * @param tone     colour of the value: "good", "info", "warn" or "bad"
 * @param facts    a few lines below the value
 * @param headline short summary for the line at the top of the start page (e.g. "12 chunk loaders active"), or null
 * @param page     page a click opens (e.g. "/chunks"), or null
 * @param link     text of the link at the bottom (e.g. "To the chunk loaders"), or null
 */
public record WebTile(String label, String value, String unit, String tone, List<Fact> facts,
                      @Nullable String headline, @Nullable String page, @Nullable String link) {

    /** One line of a tile: label, value and its colour ("", "good", "info", "warn", "bad"). */
    public record Fact(String label, String value, String tone) {
    }
}

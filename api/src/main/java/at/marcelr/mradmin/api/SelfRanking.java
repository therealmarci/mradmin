package at.marcelr.mradmin.api;

import java.util.List;
import java.util.UUID;

/**
 * A ranking of a module for the statistics page of the player area (e.g. MRpvp: most duels won), see
 * {@link WebModule#rankings}. MRadmin leaves out players who hid themselves from rankings, shows the best ones and
 * the viewer's own place. Since API 4.
 *
 * @param id      stable id within the module, e.g. "wins"
 * @param title   heading in the requested language, e.g. "Duels won"
 * @param entries all players, best first - {@code value} as shown (e.g. "42" or "61 %")
 */
public record SelfRanking(String id, String title, List<Entry> entries) {

    public record Entry(UUID player, String name, String value) {
    }
}

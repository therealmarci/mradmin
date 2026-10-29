package at.marcelr.mradmin.api;

import java.util.List;

/**
 * One area of a player's own page (e.g. "Chunk loaders", "Duels"), see {@link WebModule#selfAreas}. MRadmin shows it
 * as its own page and the {@code summary} as a tile on the start page. All texts in the viewer's language. Since API 4.
 *
 * @param id      stable id within the module, e.g. "loaders" - part of the address (/me/mrchunks/loaders)
 * @param title   name in the navigation, e.g. "Chunk loaders"
 * @param icon    one emoji, e.g. "🧱"
 * @param summary a few blocks for the start page tile (usually one {@link SelfBlock.Stats})
 * @param blocks  the content of the area's page, top to bottom
 */
public record SelfArea(String id, String title, String icon, List<SelfBlock> summary, List<SelfBlock> blocks) {
}

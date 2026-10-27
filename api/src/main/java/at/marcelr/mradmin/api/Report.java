package at.marcelr.mradmin.api;

import org.bukkit.Location;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A problem of a player that a module reports to the team (see {@link MRadminService#report}), e.g. a chunk loader
 * locked because of lag. MRadmin turns it into a ticket for the player.
 *
 * @param key      the module's own id of the problem (e.g. the loader id): a second report with the same key goes into
 *                 the ticket already open, {@link MRadminService#resolve} adds a note to it. Also passed to
 *                 {@link AdminModule#openHealth} by the ticket's "open" button.
 * @param kind     ticket category, e.g. "chunkloader-lock" - its name comes from MRadmin's language file
 *                 (ticket-categories.&lt;kind&gt;), otherwise the kind itself is shown
 * @param subject  short title (at most 100 characters are used)
 * @param text     details, plain text with line breaks
 * @param location where it happened (teleport button in the ticket), or null
 * @param push     web push kind for members who subscribed to it (e.g. "chunks-locked"), or null for none
 */
public record Report(String key, String kind, UUID player, String playerName, String subject, String text,
                     @Nullable Location location, @Nullable String push) {
}

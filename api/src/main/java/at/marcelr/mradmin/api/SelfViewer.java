package at.marcelr.mradmin.api;

import java.util.Set;
import java.util.UUID;

/**
 * The player looking at their own page in the web interface (player area, see {@link WebModule#selfAreas}). Since
 * API 4.
 *
 * @param player      the player - a module shows and changes only what belongs to them
 * @param name        their name
 * @param lang        language of the page ("de" or "en") - build every text in it
 * @param permissions those of {@link WebModule#selfPermissions()} the player has (checked by MRadmin beforehand,
 *                    also while they are offline - with LuckPerms)
 */
public record SelfViewer(UUID player, String name, String lang, Set<String> permissions) {

    public boolean has(String permission) {
        return permissions.contains(permission);
    }
}

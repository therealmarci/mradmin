package at.marcelr.mradmin.api;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The member of the web interface who runs an action - for messages to players and logs ("by Marci").
 * @param player their Minecraft account, if the web account is linked to one
 */
public record WebUser(String name, @Nullable UUID player) {
}

package at.marcelr.mradmin.api;

import org.jspecify.annotations.Nullable;

/**
 * A hint on the start page of MRadmin's web interface ("3 chunk loaders locked because of lag"), see
 * {@link WebModule#alerts()}. Only members who may see the module get it.
 *
 * @param tone "bad", "warn" or "info" - the worst tone comes first
 * @param text the hint, already in the module's language
 * @param page page of the web interface a click opens (e.g. "/chunks"), or null
 */
public record WebAlert(String tone, String text, @Nullable String page) {
}

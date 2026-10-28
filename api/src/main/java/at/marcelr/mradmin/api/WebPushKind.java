package at.marcelr.mradmin.api;

/**
 * A kind of web push notification a module sends (see {@link WebModule#pushKinds()} and {@link Report#push()}).
 * Members who may see the module choose in their account whether they want it. Since API 3.
 *
 * @param key       the kind, e.g. "chunks-locked" - unique over all modules, also the key of the member's choice
 * @param label     name in the module's language, e.g. "Locked chunk loaders"
 * @param hint      one sentence what it is about
 * @param icon      one emoji for the list, e.g. "🔒"
 * @param byDefault on until the member changes it
 */
public record WebPushKind(String key, String label, String hint, String icon, boolean byDefault) {
}

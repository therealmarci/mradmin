package at.marcelr.mradmin.api;

import org.bukkit.inventory.ItemStack;

/**
 * One conspicuous thing in MRadmin's server health menu, as a ready-made icon in the module's language.
 * @param id passed back to {@link AdminModule#openHealth} on a click
 */
public record HealthEntry(String id, ItemStack icon) {
}

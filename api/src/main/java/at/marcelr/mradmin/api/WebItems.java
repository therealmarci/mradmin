package at.marcelr.mradmin.api;

import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Items as plain data for the web interface - MRadmin draws them (with the game's pictures) and edits them in its item
 * editor. A module puts {@link #describe} into its web data and turns what the editor sends back into an item with
 * {@link #build}. Since API version 2 ({@link MRadminService#items()}).
 * <p>
 * The data (all keys optional except {@code type}):
 * <pre>
 *  type        "diamond_sword" (namespace "minecraft:" left out, others written out)
 *  amount      1 ...
 *  maxStack    largest stack of the item (only from describe)
 *  nameKey     translation key of the item's normal name, e.g. "item.minecraft.potion.effect.healing" (describe)
 *  name        own name as MiniMessage, or null
 *  title       own name as display: [{text, color "#rrggbb", b, i, u, s, o}] (describe)
 *  lore        lines as MiniMessage                    loreView  lines as display, like title (describe)
 *  enchants    [[key, level]] - stored enchantments of an enchanted book under "stored"
 *  unbreakable true/false        damage, max  durability used / total (max 0 = none)
 *  potion      {base, color "#rrggbb" | null, effects: [{type, level, seconds (-1 = endless), ambient, particles, icon}]}
 *  color       dyed colour "#rrggbb" (leather armour ...), or null
 *  trim        {material, pattern} of armour, or null
 *  flight      flight duration of a firework rocket (1-3), or null
 *  glint       enchantment glint forced on (true) or off (false), null = normal
 *  data        the complete item (Base64) - {@link #build} starts from it, so whatever the editor does not show stays
 * </pre>
 */
public interface WebItems {

    /** The item as data, or null for an empty slot. Main thread. */
    @Nullable Map<String, Object> describe(@Nullable ItemStack item);

    /**
     * The item the data describes (after the item editor): starts from {@code data} if there is one and applies the
     * other values. Main thread.
     *
     * @throws IllegalArgumentException with a short reason ("item-type", "item-enchant" ...) if a value is invalid
     */
    ItemStack build(Map<String, Object> data);
}

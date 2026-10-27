package at.marcelr.mradmin.items;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the item editor offers, straight from the server's registries - so a new Minecraft version brings its new
 * items, enchantments and effects without an update of MRadmin. Built once, on the server thread.
 */
public final class ItemCatalog {

    private ItemCatalog() {
    }

    /**
     * items        [{id, key, stack, durability, kinds: potion | trim | rocket | book | dye}]
     * enchantments [{id, key, max, curse, items: [ids], conflicts: [ids]}]
     * potions      [{id, key}]       effects [{id, key, instant, category}]
     * trimMaterials, trimPatterns    [{id, key}]
     */
    public static Map<String, Object> build() {
        var access = RegistryAccess.registryAccess();
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, ItemStack> samples = new LinkedHashMap<>();
        for (ItemType type : access.getRegistry(RegistryKey.ITEM)) {
            String id = ItemCodec.key(type.getKey());
            Material material = Material.matchMaterial(type.getKey().toString());
            if (id.equals("air") || material == null) {
                continue;
            }
            List<String> kinds = new ArrayList<>();
            Class<?> meta = type.getItemMetaClass();
            if (PotionMeta.class.isAssignableFrom(meta)) {
                kinds.add("potion");
            }
            if (Tag.ITEMS_TRIMMABLE_ARMOR.isTagged(material)) {
                kinds.add("trim");
            }
            if (FireworkMeta.class.isAssignableFrom(meta)) {
                kinds.add("rocket");
            }
            if (EnchantmentStorageMeta.class.isAssignableFrom(meta)) {
                kinds.add("book");
            }
            if (ItemCodec.dyeable(material)) {
                kinds.add("dye");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("key", type.translationKey());
            item.put("stack", type.getMaxStackSize());
            item.put("durability", (int) type.getMaxDurability());
            item.put("kinds", kinds);
            items.add(item);
            samples.put(id, type.createItemStack());
        }

        List<Map<String, Object>> enchantments = new ArrayList<>();
        List<Enchantment> all = new ArrayList<>();
        access.getRegistry(RegistryKey.ENCHANTMENT).forEach(all::add);
        for (Enchantment enchantment : all) {
            List<String> fits = new ArrayList<>();
            samples.forEach((id, sample) -> {
                if (enchantment.canEnchantItem(sample)) {
                    fits.add(id);
                }
            });
            List<String> conflicts = new ArrayList<>();
            for (Enchantment other : all) {
                if (other != enchantment && enchantment.conflictsWith(other)) {
                    conflicts.add(ItemCodec.key(other.getKey()));
                }
            }
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", ItemCodec.key(enchantment.getKey()));
            e.put("key", key(enchantment.description(), "enchantment.minecraft." + e.get("id")));
            e.put("max", enchantment.getMaxLevel());
            e.put("curse", enchantment.isCursed());
            e.put("items", fits);
            e.put("conflicts", conflicts);
            enchantments.add(e);
        }

        List<Map<String, Object>> potions = new ArrayList<>();
        for (PotionType potion : access.getRegistry(RegistryKey.POTION)) {
            String id = ItemCodec.key(potion.getKey());
            // Names go by the effect, without "long_"/"strong_" (as the game does: "Potion of Healing").
            String base = id.replaceFirst("^(long|strong)_", "");
            potions.add(Map.of("id", id, "key", "item.minecraft.potion.effect." + base));
        }
        List<Map<String, Object>> effects = new ArrayList<>();
        for (PotionEffectType effect : access.getRegistry(RegistryKey.MOB_EFFECT)) {
            effects.add(Map.of("id", ItemCodec.key(effect.getKey()), "key", effect.translationKey(),
                    "instant", effect.isInstant(), "category", effect.getEffectCategory().name().toLowerCase(Locale.ROOT)));
        }
        List<Map<String, Object>> materials = new ArrayList<>();
        for (TrimMaterial material : access.getRegistry(RegistryKey.TRIM_MATERIAL)) {
            String id = ItemCodec.key(access.getRegistry(RegistryKey.TRIM_MATERIAL).getKeyOrThrow(material));
            materials.add(Map.of("id", id, "key", key(material.description(), "trim_material.minecraft." + id)));
        }
        List<Map<String, Object>> patterns = new ArrayList<>();
        for (TrimPattern pattern : access.getRegistry(RegistryKey.TRIM_PATTERN)) {
            String id = ItemCodec.key(access.getRegistry(RegistryKey.TRIM_PATTERN).getKeyOrThrow(pattern));
            patterns.add(Map.of("id", id, "key", key(pattern.description(), "trim_pattern.minecraft." + id)));
        }

        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("items", items);
        catalog.put("enchantments", enchantments);
        catalog.put("potions", potions);
        catalog.put("effects", effects);
        catalog.put("trimMaterials", materials);
        catalog.put("trimPatterns", patterns);
        return catalog;
    }

    private static String key(Component description, String fallback) {
        return description instanceof TranslatableComponent t ? t.key() : fallback;
    }
}

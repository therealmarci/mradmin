package at.marcelr.mradmin.items;

import at.marcelr.mradmin.api.WebItems;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import io.papermc.paper.text.PaperComponents;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.Color;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Items as data for the web interface and back - the format is described at {@link WebItems}. Names and lore are
 * MiniMessage with colours and styles only (no click, hover or other tags), so an edited item cannot carry commands.
 */
public final class ItemCodec implements WebItems {

    static final int MAX_TEXT = 256;
    static final int MAX_LORE = 32;
    static final int MAX_EFFECTS = 16;

    /** MiniMessage limited to how text looks. */
    private static final MiniMessage STYLE_ONLY = MiniMessage.builder()
            .tags(TagResolver.resolver(StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(),
                    StandardTags.rainbow(), StandardTags.transition(), StandardTags.reset(), StandardTags.shadowColor()))
            .build();

    // ------------------------------------------------------------------ describe

    @Override
    public @Nullable Map<String, Object> describe(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", key(item.getType().getKey()));
        data.put("amount", item.getAmount());
        data.put("maxStack", item.getMaxStackSize());
        ItemMeta meta = item.getItemMeta();
        Component normal = item.effectiveName();
        String typeKey = item.getType().asItemType() == null ? item.getType().translationKey()
                : item.getType().asItemType().translationKey();
        String nameKey = normal instanceof TranslatableComponent t && !(meta != null && meta.hasCustomName()) ? t.key() : typeKey;
        // Potions are named by their base potion ("Potion of Healing"); the client does that, not the server.
        if (meta instanceof PotionMeta potion && potion.hasBasePotionType() && nameKey.equals(typeKey)) {
            nameKey = typeKey + ".effect." + key(potion.getBasePotionType().getKey()).replaceFirst("^(long|strong)_", "");
        }
        data.put("nameKey", nameKey);
        if (meta != null && meta.hasCustomName() && meta.customName() != null) {
            data.put("name", mini(meta.customName()));
            data.put("title", spans(meta.customName().decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.TRUE)));
        } else if (meta != null && meta.hasItemName() && meta.itemName() != null) {
            data.put("name", null);
            data.put("title", spans(meta.itemName()));
        } else {
            data.put("name", null);
            data.put("title", null);
        }
        List<String> lore = new ArrayList<>();
        List<Object> loreView = new ArrayList<>();
        if (meta != null && meta.hasLore() && meta.lore() != null) {
            for (Component line : meta.lore()) {
                lore.add(mini(line));
                loreView.add(spans(line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.TRUE)
                        .colorIfAbsent(TextColor.color(0xAA00AA))));
            }
        }
        data.put("lore", lore);
        data.put("loreView", loreView);
        data.put("enchants", enchants(item.getEnchantments()));
        if (meta instanceof EnchantmentStorageMeta book) {
            data.put("stored", enchants(book.getStoredEnchants()));
        }
        data.put("unbreakable", meta != null && meta.isUnbreakable());
        int max = meta instanceof Damageable d && d.hasMaxDamage() ? d.getMaxDamage() : item.getType().getMaxDurability();
        data.put("max", max);
        data.put("damage", meta instanceof Damageable d ? d.getDamage() : 0);
        if (meta instanceof PotionMeta potion) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("base", potion.hasBasePotionType() ? key(potion.getBasePotionType().getKey()) : null);
            view.put("color", potion.hasColor() ? hex(potion.getColor()) : null);
            List<Map<String, Object>> effects = new ArrayList<>();
            for (PotionEffect effect : potion.getCustomEffects()) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("type", key(effect.getType().getKey()));
                e.put("level", effect.getAmplifier() + 1);
                e.put("seconds", effect.isInfinite() ? -1 : effect.getDuration() / 20);
                e.put("ambient", effect.isAmbient());
                e.put("particles", effect.hasParticles());
                e.put("icon", effect.hasIcon());
                effects.add(e);
            }
            view.put("effects", effects);
            // What the base potion does (only for the display).
            List<Map<String, Object>> base = new ArrayList<>();
            if (potion.hasBasePotionType()) {
                for (PotionEffect effect : potion.getBasePotionType().getPotionEffects()) {
                    base.add(Map.of("type", key(effect.getType().getKey()), "level", effect.getAmplifier() + 1,
                            "seconds", effect.isInfinite() ? -1 : effect.getDuration() / 20));
                }
            }
            view.put("baseEffects", base);
            data.put("potion", view);
        }
        data.put("color", item.hasData(DataComponentTypes.DYED_COLOR)
                ? hex(item.getData(DataComponentTypes.DYED_COLOR).color()) : null);
        if (meta instanceof ArmorMeta armor && armor.hasTrim()) {
            ArmorTrim trim = armor.getTrim();
            data.put("trim", Map.of("material", key(registryKey(RegistryKey.TRIM_MATERIAL, trim.getMaterial())),
                    "pattern", key(registryKey(RegistryKey.TRIM_PATTERN, trim.getPattern()))));
        } else {
            data.put("trim", null);
        }
        data.put("flight", meta instanceof FireworkMeta firework ? firework.getPower() : null);
        data.put("glint", meta != null && meta.hasEnchantmentGlintOverride() ? meta.getEnchantmentGlintOverride() : null);
        data.put("data", Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        return data;
    }

    private static List<Object[]> enchants(Map<Enchantment, Integer> enchantments) {
        List<Object[]> result = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> e : enchantments.entrySet()) {
            result.add(new Object[]{key(e.getKey().getKey()), e.getValue()});
        }
        return result;
    }

    /** A component as MiniMessage, without the "not italic" every own name gets. */
    private static String mini(Component component) {
        Component plain = component.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE
                ? component.decoration(TextDecoration.ITALIC, TextDecoration.State.NOT_SET) : component;
        return MiniMessage.miniMessage().serialize(plain);
    }

    /** A component as pieces of text with their look: {text, color, b, i, u, s, o}. */
    static List<Map<String, Object>> spans(Component component) {
        List<Map<String, Object>> spans = new ArrayList<>();
        Deque<Style> styles = new ArrayDeque<>();
        styles.push(Style.empty());
        PaperComponents.flattener().flatten(component, new FlattenerListener() {
            @Override
            public void pushStyle(Style style) {
                styles.push(styles.peek().merge(style));
            }

            @Override
            public void component(String text) {
                if (text.isEmpty()) {
                    return;
                }
                Style style = styles.peek();
                Map<String, Object> span = new LinkedHashMap<>();
                span.put("text", text);
                if (style.color() != null) {
                    span.put("color", style.color().asHexString());
                }
                put(span, "b", style.decoration(TextDecoration.BOLD));
                put(span, "i", style.decoration(TextDecoration.ITALIC));
                put(span, "u", style.decoration(TextDecoration.UNDERLINED));
                put(span, "s", style.decoration(TextDecoration.STRIKETHROUGH));
                put(span, "o", style.decoration(TextDecoration.OBFUSCATED));
                spans.add(span);
            }

            @Override
            public void popStyle(Style style) {
                if (styles.size() > 1) {
                    styles.pop();
                }
            }
        });
        return spans;
    }

    private static void put(Map<String, Object> span, String key, TextDecoration.State state) {
        if (state == TextDecoration.State.TRUE) {
            span.put(key, true);
        }
    }

    // ------------------------------------------------------------------ build

    @Override
    public ItemStack build(Map<String, Object> data) {
        Material type = material(data.get("type"));
        ItemStack item;
        if (data.get("data") instanceof String encoded && !encoded.isBlank()) {
            try {
                item = ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded.trim()));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("item-data");
            }
            if (item.getType() != type) {
                item = item.withType(type);
            }
        } else {
            item = ItemStack.of(type);
        }
        int amount = integer(data.getOrDefault("amount", 1), "item-amount");
        if (amount < 1 || amount > item.getMaxStackSize()) {
            throw new IllegalArgumentException("item-amount");
        }
        item.setAmount(amount);
        ItemStack target = item;
        ItemMeta meta = target.getItemMeta();
        if (meta == null) {
            return target;
        }
        if (data.containsKey("name")) {
            meta.customName(text(data.get("name"), "item-name"));
        }
        if (data.get("lore") instanceof List<?> lines) {
            if (lines.size() > MAX_LORE) {
                throw new IllegalArgumentException("item-lore");
            }
            List<Component> lore = new ArrayList<>();
            for (Object line : lines) {
                Component c = text(line, "item-lore");
                lore.add(c == null ? Component.empty() : c);
            }
            meta.lore(lore.isEmpty() ? null : lore);
        }
        if (data.get("enchants") instanceof List<?> list) {
            for (Enchantment e : List.copyOf(meta.getEnchants().keySet())) {
                meta.removeEnchant(e);
            }
            enchantments(list).forEach((e, level) -> meta.addEnchant(e, level, true));
        }
        if (meta instanceof EnchantmentStorageMeta book && data.get("stored") instanceof List<?> list) {
            for (Enchantment e : List.copyOf(book.getStoredEnchants().keySet())) {
                book.removeStoredEnchant(e);
            }
            enchantments(list).forEach((e, level) -> book.addStoredEnchant(e, level, true));
        }
        if (data.get("unbreakable") instanceof Boolean unbreakable) {
            meta.setUnbreakable(unbreakable);
        }
        if (meta instanceof Damageable damageable && data.containsKey("damage")) {
            int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : type.getMaxDurability();
            int damage = integer(data.get("damage"), "item-damage");
            if (damage < 0 || (max > 0 && damage >= max)) {
                throw new IllegalArgumentException("item-damage");
            }
            if (max > 0) {
                damageable.setDamage(damage);
            }
        }
        if (meta instanceof PotionMeta potion && data.get("potion") instanceof Map<?, ?> view) {
            potion(potion, view);
        }
        if (meta instanceof ArmorMeta armor && data.containsKey("trim")) {
            armor.setTrim(trim(data.get("trim")));
        }
        if (meta instanceof FireworkMeta firework && data.get("flight") != null) {
            int flight = integer(data.get("flight"), "item-flight");
            if (flight < 0 || flight > 3) {
                throw new IllegalArgumentException("item-flight");
            }
            firework.setPower(flight);
        }
        if (data.containsKey("glint")) {
            meta.setEnchantmentGlintOverride(data.get("glint") instanceof Boolean b ? b : null);
        }
        target.setItemMeta(meta);
        if (data.containsKey("color")) {
            Color color = color(data.get("color"), "item-color");
            if (color == null) {
                target.unsetData(DataComponentTypes.DYED_COLOR);
            } else if (dyeable(type)) {
                target.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(color));
            }
        }
        return target;
    }

    private static void potion(PotionMeta potion, Map<?, ?> view) {
        Object base = view.get("base");
        if (base == null || (base instanceof String s && s.isBlank())) {
            potion.setBasePotionType(null);
        } else {
            PotionType type = registry(RegistryKey.POTION, base, "item-potion");
            potion.setBasePotionType(type);
        }
        potion.setColor(color(view.get("color"), "item-potion"));
        potion.clearCustomEffects();
        if (view.get("effects") instanceof List<?> effects) {
            if (effects.size() > MAX_EFFECTS) {
                throw new IllegalArgumentException("item-effect");
            }
            for (Object o : effects) {
                if (!(o instanceof Map<?, ?> e)) {
                    throw new IllegalArgumentException("item-effect");
                }
                PotionEffectType type = registry(RegistryKey.MOB_EFFECT, e.get("type"), "item-effect");
                int level = integer(e.get("level"), "item-effect");
                int seconds = integer(e.get("seconds"), "item-effect");
                if (level < 1 || level > 255 || seconds == 0 || seconds < -1 || seconds > 86_400) {
                    throw new IllegalArgumentException("item-effect");
                }
                potion.addCustomEffect(new PotionEffect(type, seconds == -1 ? PotionEffect.INFINITE_DURATION : seconds * 20,
                        level - 1, Boolean.TRUE.equals(e.get("ambient")), !Boolean.FALSE.equals(e.get("particles")),
                        !Boolean.FALSE.equals(e.get("icon"))), true);
            }
        }
    }

    private static @Nullable ArmorTrim trim(Object value) {
        if (!(value instanceof Map<?, ?> trim)) {
            return null;
        }
        TrimMaterial material = registry(RegistryKey.TRIM_MATERIAL, trim.get("material"), "item-trim");
        TrimPattern pattern = registry(RegistryKey.TRIM_PATTERN, trim.get("pattern"), "item-trim");
        return new ArmorTrim(material, pattern);
    }

    private static Map<Enchantment, Integer> enchantments(List<?> list) {
        Map<Enchantment, Integer> result = new LinkedHashMap<>();
        for (Object entry : list) {
            if (!(entry instanceof List<?> pair) || pair.size() != 2) {
                throw new IllegalArgumentException("item-enchant");
            }
            Enchantment enchantment = registry(RegistryKey.ENCHANTMENT, pair.get(0), "item-enchant");
            int level = integer(pair.get(1), "item-enchant");
            if (level < 1 || level > 255) {
                throw new IllegalArgumentException("item-enchant");
            }
            result.put(enchantment, level);
        }
        return result;
    }

    /** Own text (MiniMessage, looks only); null or blank = none. */
    private static @Nullable Component text(Object value, String error) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String s) || s.length() > MAX_TEXT) {
            throw new IllegalArgumentException(error);
        }
        if (s.isBlank()) {
            return null;
        }
        return STYLE_ONLY.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static Material material(Object value) {
        Material type = value instanceof String s && namespaced(s) != null ? Material.matchMaterial(s.trim()) : null;
        if (type == null || !type.isItem() || type.isAir()) {
            throw new IllegalArgumentException("item-type");
        }
        return type;
    }

    /** Leather armour and the like - the items a cauldron can wash the dye off (there is no own "dyeable" tag). */
    static boolean dyeable(Material type) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ITEM);
        return type.asItemType() != null && registry.hasTag(ItemTypeTagKeys.CAULDRON_CAN_REMOVE_DYE)
                && registry.getTag(ItemTypeTagKeys.CAULDRON_CAN_REMOVE_DYE)
                .contains(TypedKey.create(RegistryKey.ITEM, type.asItemType().key()));
    }

    private static <T extends Keyed> T registry(RegistryKey<T> key, Object value, String error) {
        NamespacedKey k = value instanceof String s ? namespaced(s) : null;
        T entry = k == null ? null : RegistryAccess.registryAccess().getRegistry(key).get(k);
        if (entry == null) {
            throw new IllegalArgumentException(error);
        }
        return entry;
    }

    private static <T extends Keyed> NamespacedKey registryKey(RegistryKey<T> key, T value) {
        NamespacedKey k = RegistryAccess.registryAccess().getRegistry(key).getKey(value);
        return k == null ? NamespacedKey.minecraft("unknown") : k;
    }

    private static int integer(Object value, String error) {
        if (value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
            return n.intValue();
        }
        throw new IllegalArgumentException(error);
    }

    private static @Nullable Color color(Object value, String error) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            return null;
        }
        if (value instanceof String s && s.matches("#[0-9a-fA-F]{6}")) {
            return Color.fromRGB(Integer.parseInt(s.substring(1), 16));
        }
        throw new IllegalArgumentException(error);
    }

    static String hex(Color color) {
        return String.format(Locale.ROOT, "#%06x", color.asRGB());
    }

    /** "minecraft:stone" -> "stone"; other namespaces stay. */
    static String key(NamespacedKey key) {
        return key.getNamespace().equals(NamespacedKey.MINECRAFT) ? key.getKey() : key.toString();
    }

    /** "stone" / "minecraft:stone" as key; null if it is no valid key. */
    private static @Nullable NamespacedKey namespaced(String text) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 128 || !Key.parseable(value)) {
            return null;
        }
        Key key = Key.key(value);
        return new NamespacedKey(key.namespace(), key.value());
    }
}

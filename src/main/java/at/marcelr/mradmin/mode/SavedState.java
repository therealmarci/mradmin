package at.marcelr.mradmin.mode;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Everything about a player that the admin mode changes, so it can be put back exactly: position, game mode,
 * inventory (incl. armour and off hand), selected slot, XP, health, hunger, effects, fire, air, flying.
 * Items are stored with Paper's lossless binary format (all components, enchantments, names ...).
 */
public record SavedState(
        UUID player,
        String name,
        Instant since,
        String world,
        double x, double y, double z, float yaw, float pitch,
        GameMode gameMode,
        ItemStack[] inventory,
        int heldSlot,
        int level, float exp, int totalExperience,
        double health, double absorption, int food, float saturation, float exhaustion,
        List<PotionEffect> effects,
        int fireTicks, int freezeTicks, int remainingAir, float fallDistance,
        boolean allowFlight, boolean flying
) {

    public static SavedState capture(Player p) {
        Location l = p.getLocation();
        return new SavedState(p.getUniqueId(), p.getName(), Instant.now(),
                l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch(),
                p.getGameMode(),
                cloneAll(p.getInventory().getContents()),
                p.getInventory().getHeldItemSlot(),
                p.getLevel(), p.getExp(), p.getTotalExperience(),
                p.getHealth(), p.getAbsorptionAmount(), p.getFoodLevel(), p.getSaturation(), p.getExhaustion(),
                List.copyOf(p.getActivePotionEffects()),
                p.getFireTicks(), p.getFreezeTicks(), p.getRemainingAir(), p.getFallDistance(),
                p.getAllowFlight(), p.isFlying());
    }

    /** The saved position, or null if its world is not loaded. */
    public Location location() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    /** Puts everything except the position back (teleporting is up to the caller). */
    public void apply(Player p) {
        p.setGameMode(gameMode);
        p.getInventory().setContents(cloneAll(inventory));
        p.getInventory().setHeldItemSlot(Math.clamp(heldSlot, 0, 8));
        p.setItemOnCursor(null);
        p.setLevel(level);
        p.setExp(Math.clamp(exp, 0f, 1f));
        p.setTotalExperience(totalExperience);
        var maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20 : maxHealth.getValue();
        p.setHealth(Math.clamp(health, 0.5, max));
        p.setAbsorptionAmount(Math.max(0, absorption));
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setExhaustion(exhaustion);
        for (PotionEffect effect : List.copyOf(p.getActivePotionEffects())) {
            p.removePotionEffect(effect.getType());
        }
        p.addPotionEffects(effects);
        p.setFireTicks(fireTicks);
        p.setFreezeTicks(freezeTicks);
        p.setRemainingAir(remainingAir);
        p.setFallDistance(fallDistance);
        p.setAllowFlight(allowFlight || gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR);
        p.setFlying(flying && p.getAllowFlight());
        p.updateInventory();
    }

    private static ItemStack[] cloneAll(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] == null || items[i].isEmpty() ? null : items[i].clone();
        }
        return copy;
    }

    // ----------------------------------------------------------------- file

    public void save(File file) throws IOException {
        YamlConfiguration yml = new YamlConfiguration();
        yml.options().setHeader(List.of("MRadmin admin mode - saved state of " + name + ". Restored by /mradmin mode off.",
                "Do not edit or delete while the player is in admin mode, otherwise their items are lost!"));
        yml.set("player", player.toString());
        yml.set("name", name);
        yml.set("since", since.toString());
        yml.set("location.world", world);
        yml.set("location.x", x);
        yml.set("location.y", y);
        yml.set("location.z", z);
        yml.set("location.yaw", yaw);
        yml.set("location.pitch", pitch);
        yml.set("gamemode", gameMode.name());
        // Empty slots must stay in place, so store the whole array (air for empty slots).
        ItemStack[] slots = new ItemStack[inventory.length];
        for (int i = 0; i < inventory.length; i++) {
            slots[i] = inventory[i] == null ? ItemStack.empty() : inventory[i];
        }
        yml.set("inventory", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(slots)));
        yml.set("held-slot", heldSlot);
        yml.set("xp.level", level);
        yml.set("xp.exp", exp);
        yml.set("xp.total", totalExperience);
        yml.set("health", health);
        yml.set("absorption", absorption);
        yml.set("food", food);
        yml.set("saturation", saturation);
        yml.set("exhaustion", exhaustion);
        yml.set("effects", new ArrayList<>(effects));
        yml.set("fire-ticks", fireTicks);
        yml.set("freeze-ticks", freezeTicks);
        yml.set("air", remainingAir);
        yml.set("fall-distance", fallDistance);
        yml.set("allow-flight", allowFlight);
        yml.set("flying", flying);
        // Write to a temp file first: a crash while writing must never leave a half file behind.
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        Files.writeString(tmp.toPath(), yml.saveToString(), StandardCharsets.UTF_8);
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static SavedState load(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yml = new YamlConfiguration();
        yml.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(yml.getString("inventory", "")));
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].isEmpty()) {
                items[i] = null;
            }
        }
        List<PotionEffect> effects = new ArrayList<>();
        for (Object o : yml.getList("effects", List.of())) {
            if (o instanceof PotionEffect effect) {
                effects.add(effect);
            }
        }
        return new SavedState(UUID.fromString(yml.getString("player", "")), yml.getString("name", "?"),
                Instant.parse(yml.getString("since", Instant.now().toString())),
                yml.getString("location.world", ""), yml.getDouble("location.x"), yml.getDouble("location.y"),
                yml.getDouble("location.z"), (float) yml.getDouble("location.yaw"), (float) yml.getDouble("location.pitch"),
                GameMode.valueOf(yml.getString("gamemode", "SURVIVAL")),
                items, yml.getInt("held-slot"),
                yml.getInt("xp.level"), (float) yml.getDouble("xp.exp"), yml.getInt("xp.total"),
                yml.getDouble("health", 20), yml.getDouble("absorption"), yml.getInt("food", 20),
                (float) yml.getDouble("saturation", 5), (float) yml.getDouble("exhaustion"),
                List.copyOf(effects),
                yml.getInt("fire-ticks"), yml.getInt("freeze-ticks"), yml.getInt("air", 300),
                (float) yml.getDouble("fall-distance"), yml.getBoolean("allow-flight"), yml.getBoolean("flying"));
    }
}

package at.marcelr.mradmin.health;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The chunks with the most entities - the usual lag sources. Goes through every entity of every world, so it costs
 * something: server thread only, and callers keep the result for a while (server health menu, web).
 */
public final class HotChunks {

    /** One chunk: where, how many entities (players do not count), how many of each type. */
    public record Chunk(World world, int x, int z, int count, Map<EntityType, Integer> types) {
    }

    private record Key(World world, int x, int z) {
    }

    private HotChunks() {
    }

    /** The {@code top} chunks with the most entities, most first. */
    public static List<Chunk> count(int top) {
        Map<Key, Map<EntityType, Integer>> perChunk = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) {
                    continue;
                }
                Location l = entity.getLocation();
                perChunk.computeIfAbsent(new Key(world, l.getBlockX() >> 4, l.getBlockZ() >> 4), k -> new HashMap<>())
                        .merge(entity.getType(), 1, Integer::sum);
            }
        }
        List<Chunk> chunks = new ArrayList<>();
        perChunk.forEach((key, types) -> chunks.add(new Chunk(key.world(), key.x(), key.z(),
                types.values().stream().mapToInt(Integer::intValue).sum(), types)));
        chunks.sort(Comparator.comparingInt(Chunk::count).reversed());
        return List.copyOf(chunks.subList(0, Math.min(top, chunks.size())));
    }
}

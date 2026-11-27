package ink.ziip.championshipscore.api.game.hotycodydusky.runtime;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.BiConsumer;

/** Nearby viewers see the closest real sample in each 2x2 tile, up to the effect's packet limit. */
final class BasinParticleFrame {
    private record Tile(int x, int y, int z) {}

    private record Sample(Location at, double distanceSquared) {}

    private static final Comparator<Sample> FARTHEST_FIRST =
            Comparator.comparingDouble(Sample::distanceSquared).reversed();

    private final Map<Long, Map<Tile, List<Location>>> chunks = new HashMap<>();
    private World world;

    void add(Location at) {
        world = at.getWorld();
        int x = at.getBlockX(), z = at.getBlockZ();
        chunks.computeIfAbsent(Chunk.getChunkKey(x >> 4, z >> 4), k -> new HashMap<>())
                .computeIfAbsent(new Tile(x >> 1, at.getBlockY(), z >> 1), k -> new ArrayList<>())
                .add(at);
    }

    void show(int radius, int limit, BiConsumer<Player, Location> send) {
        if (world == null) return;
        for (Player viewer : world.getPlayers())
            for (Location at : nearest(viewer.getLocation(), radius, limit))
                send.accept(viewer, at);
    }

    List<Location> nearest(Location center, int radius, int limit) {
        var selected = new PriorityQueue<Sample>(limit, FARTHEST_FIRST);
        int x = center.getBlockX(), z = center.getBlockZ();
        for (int cx = (x - radius) >> 4; cx <= (x + radius) >> 4; cx++) {
            for (int cz = (z - radius) >> 4; cz <= (z + radius) >> 4; cz++) {
                var tiles = chunks.get(Chunk.getChunkKey(cx, cz));
                if (tiles == null) continue;
                for (var tile : tiles.values()) {
                    double best =
                            selected.size() < limit
                                    ? radius * radius
                                    : selected.element().distanceSquared();
                    Location closest = null;
                    for (Location at : tile) {
                        double dx = at.getX() - center.getX(),
                                dy = at.getY() - center.getY(),
                                dz = at.getZ() - center.getZ();
                        double distance = dx * dx + dy * dy + dz * dz;
                        if (distance <= best) {
                            best = distance;
                            closest = at;
                        }
                    }
                    if (closest == null) continue;
                    if (selected.size() == limit) selected.remove();
                    selected.add(new Sample(closest, best));
                }
            }
        }
        return selected.stream().map(Sample::at).toList();
    }
}

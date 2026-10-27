package ink.ziip.championshipscore.api.game.tntrun.runtime;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.NumberConversions;

/** Probes the two foot-height layers without loading chunks or revisiting duplicate corners. */
final class TNTRunBlockProbe {
    private TNTRunBlockProbe() {}

    static Hit find(Location location, double bias) {
        World world = location.getWorld();
        if (world == null) return null;
        int minX = NumberConversions.floor(location.getX() - bias);
        int maxX = NumberConversions.floor(location.getX() + bias);
        int minZ = NumberConversions.floor(location.getZ() - bias);
        int maxZ = NumberConversions.floor(location.getZ() + bias);
        int feetY = location.getBlockY();
        int minY = world.getMinHeight(), maxY = world.getMaxHeight();
        for (int y = feetY; y >= feetY - 1; y--) {
            if (y < minY || y >= maxY) continue;
            Hit hit = solid(world, maxX, y, minZ);
            if (hit != null) return hit;
            if (minX != maxX || minZ != maxZ) {
                hit = solid(world, minX, y, maxZ);
                if (hit != null) return hit;
            }
            if (minX != maxX && minZ != maxZ) {
                hit = solid(world, maxX, y, maxZ);
                if (hit != null) return hit;
                hit = solid(world, minX, y, minZ);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private static Hit solid(World world, int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        Block block = world.getBlockAt(x, y, z);
        Material material = block.getType();
        return material == Material.AIR || material == Material.LIGHT
                ? null
                : new Hit(block, material);
    }

    record Hit(Block block, Material material) {}
}

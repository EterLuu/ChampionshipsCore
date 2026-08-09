package ink.ziip.championshipscore.api.game.buildmart.reference;

import ink.ziip.championshipscore.api.game.buildmart.blueprint.BlueprintBlock;
import ink.ziip.championshipscore.api.game.buildmart.blueprint.BuildMartBlueprint;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Pastes and clears blueprint footprints in the world. The reference build is made of real blocks (so it
 * renders for everyone without packets) placed at the slot's reference anchor; player griefing is
 * prevented by the handler cancelling breaks inside reference footprints. The same footprint maths is
 * reused to wipe a completed/expired build back to air.
 */
public final class ReferenceBuilder {
    private static final int BUILD_HEIGHT = 7;

    private ReferenceBuilder() {
    }

    /** Pastes a reference build inside the 7x7x7 volume above the configured floor minimum corner. */
    public static void paste(BuildMartBlueprint blueprint, Location floorMinimum) {
        World world = floorMinimum.getWorld();
        if (world == null) return;
        // Remove leftovers from a previous order before stamping the new one. This keeps the visible
        // reference and its validation volume identical even when a round ended with stray blocks.
        clearBuildArea(floorMinimum);
        Location origin = buildOrigin(floorMinimum);
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();
        for (BlueprintBlock b : blueprint.getBlocks()) {
            Block block = world.getBlockAt(baseX + b.getX(), baseY + b.getY(), baseZ + b.getZ());
            block.setBlockData(b.getBlockData(), false);
        }
    }

    /** Clears the complete reference volume, including blocks left by an earlier misaligned paste. */
    public static void clear(BuildMartBlueprint blueprint, Location floorMinimum) {
        clearBuildArea(floorMinimum);
    }

    /**
     * True when {@code worldX/Y/Z} is one of {@code blueprint}'s footprint cells anchored at
     * {@code anchor} — used to protect reference builds from being broken.
     */
    public static boolean isFootprintBlock(BuildMartBlueprint blueprint, Location floorMinimum,
                                           int worldX, int worldY, int worldZ) {
        if (floorMinimum.getWorld() == null) return false;
        Location origin = buildOrigin(floorMinimum);
        int dx = worldX - origin.getBlockX();
        int dy = worldY - origin.getBlockY();
        int dz = worldZ - origin.getBlockZ();
        for (BlueprintBlock b : blueprint.getBlocks()) {
            if (b.getX() == dx && b.getY() == dy && b.getZ() == dz) return true;
        }
        return false;
    }

    /** Blueprint origin directly above the minimum X/Y/Z corner of the 7x7 floor selection. */
    public static Location buildOrigin(Location floorMinimum) {
        return floorMinimum.clone().add(0, 1, 0);
    }

    /** Clears the complete 7x7x7 build volume above a plot floor, including extra misplaced blocks. */
    public static void clearBuildArea(Location floorMinimum) {
        World world = floorMinimum.getWorld();
        if (world == null) return;
        int minX = floorMinimum.getBlockX();
        int floorY = floorMinimum.getBlockY();
        int minZ = floorMinimum.getBlockZ();
        for (int x = minX; x < minX + BUILD_HEIGHT; x++) {
            for (int y = floorY + 1; y <= floorY + BUILD_HEIGHT; y++) {
                for (int z = minZ; z < minZ + BUILD_HEIGHT; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    /** True when a block is in the 7x7x7 build volume above the configured floor minimum corner. */
    public static boolean isBuildAreaBlock(Location floorMinimum, World world,
                                           int worldX, int worldY, int worldZ) {
        if (floorMinimum.getWorld() == null || !floorMinimum.getWorld().equals(world)) return false;
        int minX = floorMinimum.getBlockX();
        int floorY = floorMinimum.getBlockY();
        int minZ = floorMinimum.getBlockZ();
        return worldX >= minX && worldX < minX + BUILD_HEIGHT
                && worldY >= floorY + 1 && worldY <= floorY + BUILD_HEIGHT
                && worldZ >= minZ && worldZ < minZ + BUILD_HEIGHT;
    }
}

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

    private ReferenceBuilder() {
    }

    /** Pastes {@code blueprint}'s blocks at {@code anchor} (the reference origin). Main thread only. */
    public static void paste(BuildMartBlueprint blueprint, Location anchor) {
        World world = anchor.getWorld();
        if (world == null) return;
        int baseX = anchor.getBlockX();
        int baseY = anchor.getBlockY();
        int baseZ = anchor.getBlockZ();
        for (BlueprintBlock b : blueprint.getBlocks()) {
            Block block = world.getBlockAt(baseX + b.getX(), baseY + b.getY(), baseZ + b.getZ());
            block.setBlockData(b.getBlockData(), false);
        }
    }

    /** Sets every block of {@code blueprint}'s footprint at {@code anchor} back to air. Main thread only. */
    public static void clear(BuildMartBlueprint blueprint, Location anchor) {
        World world = anchor.getWorld();
        if (world == null) return;
        int baseX = anchor.getBlockX();
        int baseY = anchor.getBlockY();
        int baseZ = anchor.getBlockZ();
        for (BlueprintBlock b : blueprint.getBlocks()) {
            world.getBlockAt(baseX + b.getX(), baseY + b.getY(), baseZ + b.getZ())
                    .setType(Material.AIR, false);
        }
    }

    /**
     * True when {@code worldX/Y/Z} is one of {@code blueprint}'s footprint cells anchored at
     * {@code anchor} — used to protect reference builds from being broken.
     */
    public static boolean isFootprintBlock(BuildMartBlueprint blueprint, Location anchor, int worldX, int worldY, int worldZ) {
        if (anchor.getWorld() == null) return false;
        int dx = worldX - anchor.getBlockX();
        int dy = worldY - anchor.getBlockY();
        int dz = worldZ - anchor.getBlockZ();
        for (BlueprintBlock b : blueprint.getBlocks()) {
            if (b.getX() == dx && b.getY() == dy && b.getZ() == dz) return true;
        }
        return false;
    }
}

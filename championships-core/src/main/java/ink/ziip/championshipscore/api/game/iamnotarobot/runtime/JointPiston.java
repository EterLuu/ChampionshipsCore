package ink.ziip.championshipscore.api.game.iamnotarobot.runtime;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.JointPlaza;
import ink.ziip.championshipscore.api.game.iamnotarobot.model.JointShape;
import ink.ziip.championshipscore.util.world.NativePiston;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.Map;

/** The moving plate and the 49 independent rows of its white load. */
final class JointPiston {
    private final World world;
    private final Vector origin;
    private final Map<Block, BlockData> home = new LinkedHashMap<>();
    private int extension;

    JointPiston(World world, Vector origin) {
        this.world = world;
        this.origin = origin.clone();
    }

    int extension() {
        return extension;
    }

    private Block at(int x, int y, int z) {
        return world.getBlockAt(
                origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
    }

    void step(JointShape shape, boolean forward) {
        int plate = JointPlaza.FACE_X - extension;
        BlockFace direction = forward ? BlockFace.WEST : BlockFace.EAST;
        for (int row = 0; row < JointShape.SIZE; row++) {
            for (int col = 0; col < JointShape.SIZE; col++) {
                int y = JointPlaza.BLOCK_MIN_Y + row, z = JointPlaza.BLOCK_MIN_Z + col;
                int front = JointPlaza.WHITE_BASE_X - shape.depth(row, col) - extension;
                boolean rod = row >= 2 && row <= 4 && col >= 2 && col <= 4;
                Block destination = at(forward ? front - 1 : plate + 1, y, z);
                Material clearance = !forward && rod ? Material.IRON_BLOCK : Material.AIR;
                if (destination.getType() != clearance)
                    throw new IllegalStateException(
                            "Obstructed piston at " + destination.getLocation());
                home.computeIfAbsent(destination, Block::getBlockData);
                for (int x = front; x <= plate; x++) {
                    Block block = at(x, y, z);
                    Material expected =
                            x < plate
                                    ? JointPlaza.loadMaterial(JointPlaza.Side.EAST, x + extension)
                                    : ((row == 0 || row == 6) && (col == 0 || col == 6))
                                            ? Material.IRON_BLOCK
                                            : Material.OAK_PLANKS;
                    if (block.getType() != expected)
                        throw new IllegalStateException(
                                "Unexpected piston load at " + block.getLocation());
                    home.computeIfAbsent(block, Block::getBlockData);
                }
                if (clearance != Material.AIR) destination.setType(Material.AIR, false);
                Block base = at(forward ? plate + 1 : front - 1, y, z);
                if (!NativePiston.pushRow(
                        base,
                        destination,
                        direction,
                        forward && rod ? Material.IRON_BLOCK : Material.AIR))
                    throw new IllegalStateException("Piston push refused at " + base.getLocation());
            }
        }
        extension += forward ? 1 : -1;
    }

    void reset() {
        // A pending vanilla block entity must never materialise after a cancellation or new round.
        for (Block block : home.keySet())
            if (block.getType() == Material.MOVING_PISTON) NativePiston.remove(block);
        home.forEach(
                (block, data) -> {
                    if (!block.getBlockData().equals(data)) block.setBlockData(data, false);
                });
        home.clear();
        extension = 0;
    }
}

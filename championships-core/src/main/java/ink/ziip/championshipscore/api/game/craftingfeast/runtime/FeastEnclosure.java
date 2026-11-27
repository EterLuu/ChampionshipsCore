package ink.ziip.championshipscore.api.game.craftingfeast.runtime;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** The five exterior faces of the occupied restaurants, shared by the lesson and the match. */
final class FeastEnclosure implements AutoCloseable {
    private final List<BlockState> replaced = new ArrayList<>();

    void enclose(World world, Vector origin, Vector size) {
        int left = origin.getBlockX() - 1;
        int right = origin.getBlockX() + size.getBlockX();
        int front = origin.getBlockZ() - 1;
        int back = origin.getBlockZ() + size.getBlockZ();
        int roof = origin.getBlockY() + size.getBlockY();
        for (int y = origin.getBlockY(); y < roof; y++) {
            for (int x = left; x <= right; x++) {
                place(world.getBlockAt(x, y, front));
                place(world.getBlockAt(x, y, back));
            }
            for (int z = front + 1; z < back; z++) {
                place(world.getBlockAt(left, y, z));
                place(world.getBlockAt(right, y, z));
            }
        }
        for (int x = left; x <= right; x++)
            for (int z = front; z <= back; z++) place(world.getBlockAt(x, roof, z));
    }

    private void place(Block block) {
        if (block.getType() == Material.BARRIER) return;
        replaced.add(block.getState());
        block.setType(Material.BARRIER, false);
    }

    @Override
    public void close() {
        for (BlockState previous : replaced)
            if (previous.getBlock().getType() == Material.BARRIER) previous.update(true, false);
        replaced.clear();
    }
}

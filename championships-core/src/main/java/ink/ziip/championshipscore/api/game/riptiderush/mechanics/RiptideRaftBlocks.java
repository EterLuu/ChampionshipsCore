package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;

/** Shared physical movement for real rounds and editor trials; never moves the player. */
public final class RiptideRaftBlocks {
    private RiptideRaftBlocks() {}

    public static void move(RiptideCourseGeometry g, int from, int to, Material trail) {
        var world = g.centerAt(from).getWorld();
        var data = new ArrayList<BlockData>();
        for (int z = -g.halfLength(); z <= g.halfLength(); z++)
            for (int x = -g.halfWidth(); x <= g.halfWidth(); x++)
                data.add(
                        world.getBlockAt(g.blockX(from + z, x), g.floorY(), g.blockZ(from + z, x))
                                .getBlockData()
                                .clone());
        for (int z = -g.halfLength(); z <= g.halfLength(); z++)
            for (int x = -g.halfWidth(); x <= g.halfWidth(); x++)
                world.getBlockAt(g.blockX(from + z, x), g.floorY(), g.blockZ(from + z, x))
                        .setType(trail, false);
        int index = 0;
        for (int z = -g.halfLength(); z <= g.halfLength(); z++)
            for (int x = -g.halfWidth(); x <= g.halfWidth(); x++)
                world.getBlockAt(g.blockX(to + z, x), g.floorY(), g.blockZ(to + z, x))
                        .setBlockData(data.get(index++), false);
    }
}

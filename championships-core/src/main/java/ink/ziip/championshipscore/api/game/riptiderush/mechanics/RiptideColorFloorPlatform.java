package ink.ziip.championshipscore.api.game.riptiderush.mechanics;

import ink.ziip.championshipscore.api.game.riptiderush.course.RiptideCourseGeometry;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;

/**
 * Saves the stationary raft pattern and restores it after all color floor rounds or cancellation.
 */
public final class RiptideColorFloorPlatform {
    private final RiptideCourseGeometry geometry;
    private final List<BlockData> original = new ArrayList<>();
    private final int step;

    public RiptideColorFloorPlatform(RiptideCourseGeometry geometry, int step) {
        this.geometry = geometry;
        this.step = step;
        for (int forward = -geometry.halfLength(); forward <= geometry.halfLength(); forward++)
            for (int lateral = -geometry.halfWidth(); lateral <= geometry.halfWidth(); lateral++)
                original.add(
                        world().getBlockAt(
                                        geometry.blockX(step + forward, lateral),
                                        geometry.floorY(),
                                        geometry.blockZ(step + forward, lateral))
                                .getBlockData()
                                .clone());
    }

    public void paint(List<Material> materials) {
        if (materials.size() != original.size())
            throw new IllegalArgumentException("floor dimensions differ");
        for (int index = 0; index < original.size(); index++)
            block(index).setType(materials.get(index), false);
    }

    public void restore() {
        for (int index = 0; index < original.size(); index++)
            block(index).setBlockData(original.get(index), false);
    }

    private World world() {
        return geometry.centerAt(step).getWorld();
    }

    private org.bukkit.block.Block block(int index) {
        int forward = index / geometry.raftWidth() - geometry.halfLength();
        int lateral = index % geometry.raftWidth() - geometry.halfWidth();
        return world().getBlockAt(
                        geometry.blockX(step + forward, lateral),
                        geometry.floorY(),
                        geometry.blockZ(step + forward, lateral));
    }
}

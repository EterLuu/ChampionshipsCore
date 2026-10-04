package ink.ziip.championshipscore.api.game.riptiderush.course;

import ink.ziip.championshipscore.api.game.riptiderush.config.RiptideRushConfig;
import ink.ziip.championshipscore.api.game.riptiderush.editor.RiptideWorkshop;

import org.bukkit.Material;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Temporary solid facades owned by a round or trial, never by the saved building. */
public final class RiptideCourseReveal implements AutoCloseable {
    public static final int LEAD_BLOCKS = 10;
    static final Material MATERIAL = Material.STRIPPED_SPRUCE_WOOD;
    private final RiptideCourseGeometry geometry;
    private final int side;
    private final int height;
    private final List<HiddenLevel> hidden = new ArrayList<>();

    private record HiddenLevel(int front, List<BlockState> originals) {
        void restore() {
            originals.forEach(block -> block.update(true, false));
        }
    }

    public RiptideCourseReveal(
            RiptideRushConfig config,
            RiptideCourseGeometry geometry,
            List<RiptideCoursePlan.Level> levels) {
        this.geometry = geometry;
        side = RiptideWorkshop.halfWidth(config, geometry);
        height = config.getClearHeight();
        try {
            for (var level : levels) conceal(level);
        } catch (RuntimeException failure) {
            close();
            throw failure;
        }
    }

    private void conceal(RiptideCoursePlan.Level level) {
        int extent = level.isSideSweep() ? 0 : level.extent();
        int front = level.step() - extent;
        var originals = new ArrayList<BlockState>();
        hidden.add(new HiddenLevel(front, originals));
        var world = geometry.centerAt(level.step()).getWorld();
        for (int forward = front; forward <= level.step() + extent; forward++) {
            for (int lateral = -side; lateral <= side; lateral++) {
                for (int y = 1; y <= height; y++) {
                    var block =
                            world.getBlockAt(
                                    geometry.blockX(forward, lateral),
                                    geometry.floorY() + y,
                                    geometry.blockZ(forward, lateral));
                    // Retain tile state as well as block data; only the facade needs saved air
                    // cells.
                    if (forward == front || !block.isEmpty()) {
                        originals.add(block.getState());
                        block.setType(forward == front ? MATERIAL : Material.AIR, false);
                    }
                }
            }
        }
    }

    public void tick(int raftStep) {
        var iterator = hidden.iterator();
        while (iterator.hasNext()) {
            var level = iterator.next();
            if (level.front() - raftStep - geometry.halfLength() > LEAD_BLOCKS) continue;
            level.restore();
            iterator.remove();
        }
    }

    @Override
    public void close() {
        hidden.forEach(HiddenLevel::restore);
        hidden.clear();
    }
}

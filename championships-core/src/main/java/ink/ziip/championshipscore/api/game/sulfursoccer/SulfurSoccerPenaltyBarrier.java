package ink.ziip.championshipscore.api.game.sulfursoccer;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Temporary glass panes with exact restoration on direction changes, match end, or abort. */
final class SulfurSoccerPenaltyBarrier {
    private final Map<Block, BlockData> original = new LinkedHashMap<>();
    private final List<BoundingBox> panes = new ArrayList<>();

    void select(World world, SulfurSoccerPenaltyLayout layout, int slot) {
        clear();
        MultipleFacing data = (MultipleFacing) Material.GLASS_PANE.createBlockData();
        data.setFace(layout.alongX() ? BlockFace.NORTH : BlockFace.EAST, true);
        data.setFace(layout.alongX() ? BlockFace.SOUTH : BlockFace.WEST, true);
        for (Vector point : layout.paneBlocks(slot)) {
            Block block = world.getBlockAt(point.getBlockX(), point.getBlockY(), point.getBlockZ());
            original.put(block, block.getBlockData().clone());
            block.setBlockData(data, false);
            double x = point.getX(), y = point.getY(), z = point.getZ();
            panes.add(layout.alongX() ? new BoundingBox(x + 0.4375, y, z, x + 0.5625, y + 1, z + 1)
                    : new BoundingBox(x, y, z + 0.4375, x + 1, y + 1, z + 0.5625));
        }
    }

    /** Sweep the entire cube, rather than only its center, against the thin glass. */
    double entry(Vector from, Vector to, BoundingBox ball) {
        double first = Double.POSITIVE_INFINITY;
        for (BoundingBox pane : panes) {
            BoundingBox expanded = pane.clone().expand(ball.getWidthX() / 2, ball.getHeight() / 2, ball.getWidthZ() / 2);
            first = Math.min(first, SulfurSoccerGeometry.entry(expanded, from, to));
        }
        return first;
    }

    void clear() {
        original.forEach((block, data) -> block.setBlockData(data, false));
        original.clear();
        panes.clear();
    }
}

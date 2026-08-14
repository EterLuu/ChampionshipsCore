package ink.ziip.championshipscore.api.game.buildmart;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/** Pure placement calculations for Build Mart's chunk-aligned row of generated team bases. */
public final class BuildMartRowLayoutPlanner {
    /** Keeps adjacent structures outside both sides of the Core server's ten-chunk view distance. */
    public static final int ROW_CLEARANCE_BLOCKS = 384;
    private static final int CHUNK_SIZE = 16;

    private BuildMartRowLayoutPlanner() {
    }

    public static @NotNull Vector step(@NotNull Vector baseSize) {
        if (baseSize.getBlockX() < 1 || baseSize.getBlockY() < 1 || baseSize.getBlockZ() < 1)
            throw new IllegalArgumentException("基地模板尺寸无效：" + baseSize);
        int blocks = baseSize.getBlockX() + ROW_CLEARANCE_BLOCKS;
        int aligned = Math.floorDiv(blocks + CHUNK_SIZE - 1, CHUNK_SIZE) * CHUNK_SIZE;
        return new Vector(aligned, 0, 0);
    }

    /** Places copy 1 to the east of every known structure, aligned to a chunk boundary. */
    public static @NotNull Vector generatedOrigin(@NotNull Vector sourceOrigin, double occupiedMaxX) {
        double minimum = Math.max(occupiedMaxX, sourceOrigin.getX()) + ROW_CLEARANCE_BLOCKS;
        if (!Double.isFinite(minimum) || minimum > Integer.MAX_VALUE - CHUNK_SIZE)
            throw new IllegalArgumentException("场地坐标超出可安全规划的范围");
        int alignedX = Math.floorDiv((int) Math.ceil(minimum) + CHUNK_SIZE - 1, CHUNK_SIZE) * CHUNK_SIZE;
        return new Vector(alignedX, sourceOrigin.getY(), sourceOrigin.getZ());
    }

}

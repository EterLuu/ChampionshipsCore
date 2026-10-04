package ink.ziip.championshipscore.api.game.arena;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/** Resolves chunk-aligned replica spacing from the schematics captured by prepare. */
public final class ArenaLayoutPlanner {
    public static final int ISOLATION_PADDING_BLOCKS = 128;
    private static final int CHUNK_SIZE = 16;

    private ArenaLayoutPlanner() {}

    public static @NotNull Vector readVector(Object value) {
        if (value instanceof Vector vector) {
            if (!Double.isFinite(vector.getX())
                    || !Double.isFinite(vector.getY())
                    || !Double.isFinite(vector.getZ()))
                throw new IllegalArgumentException("场地坐标必须为有限数字");
            return vector.clone();
        }
        java.util.Map<?, ?> values;
        if (value instanceof org.bukkit.configuration.ConfigurationSection section)
            values = section.getValues(false);
        else if (value instanceof java.util.Map<?, ?> map) values = map;
        else throw new IllegalArgumentException("场地坐标必须包含 x、y、z");
        double[] coordinates = new double[3];
        String[] axes = {"x", "y", "z"};
        for (int i = 0; i < axes.length; i++) {
            if (!(values.get(axes[i]) instanceof Number number)
                    || !Double.isFinite(number.doubleValue())) {
                throw new IllegalArgumentException("场地坐标必须为有限数字：" + axes[i]);
            }
            coordinates[i] = number.doubleValue();
        }
        return new Vector(coordinates[0], coordinates[1], coordinates[2]);
    }

    public static @NotNull Vector rowStep(@NotNull Vector copySize) {
        validateSize(copySize, "arena");
        return new Vector(alignToChunk(copySize.getBlockX() + ISOLATION_PADDING_BLOCKS), 0, 0);
    }

    private static int alignToChunk(int blocks) {
        return Math.max(
                CHUNK_SIZE, Math.floorDiv(blocks + CHUNK_SIZE - 1, CHUNK_SIZE) * CHUNK_SIZE);
    }

    private static void validateSize(Vector size, String label) {
        if (size.getBlockX() < 1 || size.getBlockY() < 1 || size.getBlockZ() < 1) {
            throw new IllegalArgumentException(
                    label + " schematic has invalid dimensions: " + size);
        }
    }
}

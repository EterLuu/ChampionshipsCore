package ink.ziip.championshipscore.api.game.arena;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/** Resolves chunk-aligned replica spacing from the schematics captured by prepare. */
public final class ArenaLayoutPlanner {
    public static final int ISOLATION_PADDING_BLOCKS = 128;
    private static final int CHUNK_SIZE = 16;

    private ArenaLayoutPlanner() {
    }

    public static @NotNull Vector rowStep(@NotNull Vector copySize) {
        validateSize(copySize, "arena");
        return new Vector(alignToChunk(copySize.getBlockX() + ISOLATION_PADDING_BLOCKS), 0, 0);
    }

    public static int ringSpacing(@NotNull Vector hubSize, @NotNull Vector copySize) {
        validateSize(hubSize, "hub");
        validateSize(copySize, "copy");
        int footprint = Math.max(Math.max(hubSize.getBlockX(), hubSize.getBlockZ()),
                Math.max(copySize.getBlockX(), copySize.getBlockZ()));
        return alignToChunk(footprint + ISOLATION_PADDING_BLOCKS);
    }

    private static int alignToChunk(int blocks) {
        return Math.max(CHUNK_SIZE, Math.floorDiv(blocks + CHUNK_SIZE - 1, CHUNK_SIZE) * CHUNK_SIZE);
    }

    private static void validateSize(Vector size, String label) {
        if (size.getBlockX() < 1 || size.getBlockY() < 1 || size.getBlockZ() < 1) {
            throw new IllegalArgumentException(label + " schematic has invalid dimensions: " + size);
        }
    }
}

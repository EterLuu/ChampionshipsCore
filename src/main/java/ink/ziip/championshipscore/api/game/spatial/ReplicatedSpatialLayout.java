package ink.ziip.championshipscore.api.game.spatial;

import ink.ziip.championshipscore.api.game.arena.ArenaGrid;
import org.jetbrains.annotations.NotNull;

/** A map-owned spatial template and the fixed transforms of its reusable game instances. */
public record ReplicatedSpatialLayout<T extends SpatialTemplate<T>>(
        @NotNull T template, @NotNull ArenaGrid grid, int copyCount) {

    public ReplicatedSpatialLayout {
        if (copyCount < 1) {
            throw new IllegalArgumentException("copyCount must be positive");
        }
    }

    public @NotNull T geometry(int copyIndex) {
        if (copyIndex < 0 || copyIndex >= copyCount) {
            throw new IndexOutOfBoundsException("copyIndex=" + copyIndex + ", copyCount=" + copyCount);
        }
        return template.transform(grid.transform(copyIndex));
    }
}

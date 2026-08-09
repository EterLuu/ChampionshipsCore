package ink.ziip.championshipscore.api.game.arena;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps physical copy 0 at its hand-built source origin while placing generated copies on a ring around
 * a separate centre. Transforms therefore remain relative to copy 0 even though it is not part of the ring.
 */
public final class SourceAnchoredRingArenaGrid implements ArenaGrid {
    private final Vector sourceOrigin;
    private final RingArenaGrid generatedRing;

    public SourceAnchoredRingArenaGrid(@NotNull Vector sourceOrigin, @NotNull Vector ringCenter, int spacing) {
        this.sourceOrigin = sourceOrigin.clone();
        this.generatedRing = new RingArenaGrid(ringCenter, spacing);
    }

    @Override
    public Vector origin(int index) {
        if (index < 0) throw new IllegalArgumentException("copy index must be non-negative");
        return index == 0 ? sourceOrigin.clone() : generatedRing.origin(index);
    }
}

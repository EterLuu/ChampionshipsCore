package ink.ziip.championshipscore.api.game.arena;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps physical copy 0 at its hand-built source while placing generated copies in one deterministic row.
 * The generated row may begin beyond other structures; copy 1 is exactly {@code generatedOrigin}.
 */
public final class SourceAnchoredRowArenaGrid implements ArenaGrid {
    private final Vector sourceOrigin;
    private final Vector generatedOrigin;
    private final Vector step;

    public SourceAnchoredRowArenaGrid(@NotNull Vector sourceOrigin, @NotNull Vector generatedOrigin,
                                      @NotNull Vector step) {
        if (step.getBlockX() == 0 && step.getBlockY() == 0 && step.getBlockZ() == 0)
            throw new IllegalArgumentException("row step must not be zero");
        this.sourceOrigin = sourceOrigin.clone();
        this.generatedOrigin = generatedOrigin.clone();
        this.step = step.clone();
    }

    @Override
    public @NotNull Vector origin(int index) {
        if (index < 0) throw new IllegalArgumentException("copy index must be non-negative");
        if (index == 0) return sourceOrigin.clone();
        return generatedOrigin.clone().add(step.clone().multiply(index - 1));
    }
}

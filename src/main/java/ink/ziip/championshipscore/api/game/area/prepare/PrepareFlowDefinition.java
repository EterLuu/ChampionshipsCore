package ink.ziip.championshipscore.api.game.area.prepare;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Per-game definition of the prepare flow: which world the player must be in, where "copy 0" is (the
 * teleport target and the spot template points are configured for), and the ordered list of
 * {@link PrepareStep}s for a given area instance. Concrete subclasses live under
 * {@code api/game/area/prepare/<game>/} and cast the {@link ink.ziip.championshipscore.api.game.area.BaseArea}
 * to their typed area/Config when building steps.
 */
public abstract class PrepareFlowDefinition {

    /** World name shown in the "go to world" prompt. */
    public abstract @NotNull String worldName();

    /** True if the player is currently standing in the correct world for this flow. */
    public abstract boolean isInCorrectWorld(@NotNull Player player);

    /** Where the "teleport to copy 0" control sends the player for this area. */
    public abstract @NotNull Location copyZeroLocation(@NotNull ink.ziip.championshipscore.api.game.area.BaseArea area);

    /** Build the step list bound to the given area instance. */
    public abstract @NotNull List<PrepareStep> buildSteps(@NotNull ink.ziip.championshipscore.api.game.area.BaseArea area);
}

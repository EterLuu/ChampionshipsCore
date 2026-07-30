package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Per-game definition of the prepare flow: which world the player must be in, where "copy 0" is (the
 * teleport target and the spot template points are configured for), and the ordered list of
 * {@link PrepareStep}s for a map setup target. It deliberately has no dependency on a running instance.
 */
public abstract class PrepareFlowDefinition {

    /** World name shown in the "go to world" prompt. */
    public abstract @NotNull String worldName(@NotNull SetupTarget target);

    /** True if the player is currently standing in the correct world for this flow. */
    public abstract boolean isInCorrectWorld(@NotNull Player player, @NotNull SetupTarget target);

    /** Where the "teleport to copy 0" control sends the player for this area. */
    public abstract @NotNull Location copyZeroLocation(@NotNull SetupTarget target);

    /** Build the step list bound to the given map/configuration target. */
    public abstract @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target);
}

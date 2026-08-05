package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

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

    /** Where the primary edit-location control sends the player for this map. */
    public abstract @NotNull Location copyZeroLocation(@NotNull SetupTarget target);

    /** Name used for the flow's primary edit location in the prepare UI. */
    public @NotNull String editorLocationName(@NotNull SetupTarget target) {
        return "0 号场地";
    }

    /** Build the step list bound to the given map/configuration target. */
    public abstract @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target);

    /** Required-step validation shared by preview, validate and publish. */
    public @NotNull List<String> validate(@NotNull PrepareSession session) {
        List<String> errors = new ArrayList<>();
        if (session.getTarget().config().isWorldBindingPending() || !session.isWorldConfirmed())
            errors.add("绑定当前世界");
        for (PrepareStep step : session.getSteps()) {
            if (step.captureType() != StepCaptureType.CONFIRM_WORLD && !step.isSet(session))
                errors.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(step.displayName()));
        }
        return errors;
    }

    /**
     * Publish the edited physical map. Persistent maps only flush their loaded world; template maps
     * override this to snapshot and reload through the map storage bridge.
     */
    public boolean publish(@NotNull PrepareSession session) {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(session.getTarget().worldName());
        if (world == null) return false;
        world.save();
        return true;
    }
}

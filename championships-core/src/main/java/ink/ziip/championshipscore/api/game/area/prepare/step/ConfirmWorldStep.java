package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * Binds the map to the editor's current, already loaded world. Bingo uses the fixed-world constructor.
 */
public class ConfirmWorldStep extends PrepareStep {

    private final Predicate<Player> inCorrectWorld;
    private final boolean allowRebind;

    public ConfirmWorldStep(@NotNull Predicate<Player> inCorrectWorld, @NotNull String worldName) {
        this(inCorrectWorld, worldName, true);
    }

    public ConfirmWorldStep(@NotNull Predicate<Player> inCorrectWorld, @NotNull String worldName,
                            boolean allowRebind) {
        super("confirm_world",
                Component.text(allowRebind ? GuiConfig.text("map-editor.copy.bind-to-current-world") : GuiConfig.text("map-editor.steps.world-confirmation.confirm-the-world")),
                Component.text(allowRebind ? GuiConfig.text("map-editor.steps.world-confirmation.click-to-bind-or-change-to-the-current-world")
                        : GuiConfig.text("map-editor.copy.go-to") + worldName + GuiConfig.text("map-editor.steps.world-confirmation.click-this-to-confirm-after-entering-the-world")),
                Material.COMPASS,
                StepCaptureType.CONFIRM_WORLD);
        this.inCorrectWorld = inCorrectWorld;
        this.allowRebind = allowRebind;
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null && session.isWorldConfirmed();
    }

    @Override
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        if (allowRebind) {
            if (!session.getTarget().bindWorld(player.getWorld()))
                return Utils.formatAdminError(GuiConfig.text("map-editor.steps.world-confirmation.the-current-world-is-already-used-by-another-map-or-the-map-is-running-cannot-be-bound"));
            session.setWorldConfirmed(true);
            return Utils.formatAdminSuccess(GuiConfig.text("map-editor.steps.world-confirmation.bound-map-world") + player.getWorld().getName());
        }
        if (inCorrectWorld.test(player)) {
            session.setWorldConfirmed(true);
            return Utils.formatAdminSuccess(GuiConfig.text("map-editor.steps.world-confirmation.confirmed-game-world"));
        }
        return Utils.formatAdminError(GuiConfig.text("map-editor.steps.world-confirmation.please-go-to-the-target-editing-site-through-the-ender-pearl-first"));
    }
}

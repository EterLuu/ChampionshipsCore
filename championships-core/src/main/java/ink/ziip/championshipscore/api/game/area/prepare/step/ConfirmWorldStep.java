package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

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
                Component.text(allowRebind ? GuiConfig.text("map-editor.menus.step-list.items.confirm-world.states.rebind.title") : GuiConfig.text("map-editor.menus.step-list.items.confirm-world.title")),
                Component.text(allowRebind ? GuiConfig.line("map-editor.menus.step-list.items.confirm-world.states.rebind.lore", 0)
                        : MessageConfig.MAP_EDITOR_STEP_WORLD_CONFIRM_PROMPT.replace("%world%", worldName)),
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
                return Utils.formatAdminError(MessageConfig.MAP_EDITOR_STEP_WORLD_IN_USE);
            session.setWorldConfirmed(true);
            return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_STEP_WORLD_BOUND.replace("%world%", player.getWorld().getName()));
        }
        if (inCorrectWorld.test(player)) {
            session.setWorldConfirmed(true);
            return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_STEP_WORLD_CONFIRMED);
        }
        return Utils.formatAdminError(MessageConfig.MAP_EDITOR_STEP_GO_TO_EDITING_SITE);
    }
}

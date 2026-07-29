package ink.ziip.championshipscore.api.game.area.prepare.step;

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
 * The "go to the correct world, then confirm" step. Clicking it marks the session world-confirmed only if
 * the player is currently in the right world (per the supplied predicate); otherwise it prompts them to go
 * there (the teleport control item is the way in). State is session-only, so {@link #isSet} returns false
 * when previewing without a session.
 */
public class ConfirmWorldStep extends PrepareStep {

    private final Predicate<Player> inCorrectWorld;

    public ConfirmWorldStep(@NotNull Predicate<Player> inCorrectWorld, @NotNull String worldName) {
        super("confirm_world",
                Component.text("确认所在世界"),
                Component.text("前往 " + worldName + " 世界后点击此项确认"),
                Material.COMPASS,
                StepCaptureType.CONFIRM_WORLD);
        this.inCorrectWorld = inCorrectWorld;
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null && session.isWorldConfirmed();
    }

    @Override
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        if (inCorrectWorld.test(player)) {
            session.setWorldConfirmed(true);
            return Utils.formatAdminSuccess("已确认游戏世界。");
        }
        return Utils.formatAdminError("请先通过末影珍珠前往 0 号场地。");
    }
}

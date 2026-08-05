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
                Component.text(allowRebind ? "绑定当前世界" : "确认所在世界"),
                Component.text(allowRebind ? "点击绑定或更换为当前所在世界"
                        : "前往 " + worldName + " 世界后点击此项确认"),
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
                return Utils.formatAdminError("当前世界已被其他地图使用，或地图正在运行，无法绑定。");
            session.setWorldConfirmed(true);
            return Utils.formatAdminSuccess("已绑定地图世界：&#fff566" + player.getWorld().getName());
        }
        if (inCorrectWorld.test(player)) {
            session.setWorldConfirmed(true);
            return Utils.formatAdminSuccess("已确认游戏世界。");
        }
        return Utils.formatAdminError("请先通过末影珍珠前往目标编辑场地。");
    }
}

package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.api.game.acerace.AceRaceArea;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Session-only Ace Race course preview showing respawn crystals and progress-line particles. */
public final class AceRaceMapPreviewStep extends PrepareStep {
    public AceRaceMapPreviewStep() {
        super("map_preview", Component.text("显示赛道点位预览"),
                Component.text("切换后显示末地水晶重生点和低频进度线粒子；退出编辑会自动清理"),
                Material.END_CRYSTAL, StepCaptureType.TOGGLE);
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null;
    }

    @Override
    public String stateText(PrepareSession session) {
        AceRaceArea area = area(session);
        return area != null && area.isMapEditPreviewEnabled() ? "当前：已开启" : "当前：已关闭";
    }

    @Override
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        AceRaceArea area = area(session);
        if (area == null) return "无法找到当前 Ace Race 地图实例。";
        boolean enabled = area.toggleMapEditPreview(player);
        return enabled ? "已开启赛道点位预览：水晶可右键编辑重生点。" : "已关闭赛道点位预览。";
    }

    private static AceRaceArea area(PrepareSession session) {
        if (session == null) return null;
        SetupTarget target = session.getTarget();
        return target.plugin().getGameManager().getAceRaceManager().getArea(target.name());
    }
}

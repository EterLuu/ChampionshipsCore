package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.api.game.acerace.AceRaceConfig;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AnvilInputGui;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/** Captures an upward-extending start or finish gate from the player's WorldEdit selection. */
public final class AceRaceLineStep extends PrepareStep {
    private final boolean start;

    public AceRaceLineStep(boolean start) {
        super(start ? "start_line" : "finish_line",
                Component.text(start ? "起点线" : "终点线"),
                Component.text("用 WorldEdit 选取水平底线，触发面从该高度向上延伸；"
                        + (start ? "当前位置同时作为起跑出生点" : "用于计圈/完赛")),
                start ? Material.LIME_WOOL : Material.ORANGE_WOOL, StepCaptureType.WE_SELECTION);
        this.start = start;
    }

    private static AceRaceConfig cfg(SetupTarget target) { return (AceRaceConfig) target.config(); }

    @Override
    public boolean isSet(PrepareSession session) {
        if (session == null) return false;
        AceRaceConfig config = cfg(session.getTarget());
        return start ? config.hasStartLine() && config.getStartSpawnPoint() != null : config.hasFinishLine();
    }

    @Override
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        Vector[] selection;
        try {
            selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, true);
        } catch (Exception exception) {
            return Utils.formatAdminError("请先用 WorldEdit 选取起点线或终点线。");
        }
        int spanX = Math.abs(selection[0].getBlockX() - selection[1].getBlockX());
        int spanY = Math.abs(selection[0].getBlockY() - selection[1].getBlockY());
        int spanZ = Math.abs(selection[0].getBlockZ() - selection[1].getBlockZ());
        if (spanY != 0 || (spanX > 0 && spanZ > 0) || (spanX == 0 && spanZ == 0))
            return Utils.formatAdminError("起点线/终点线必须是同一高度、沿 X 或 Z 方向延伸的直线。");
        AceRaceConfig config = cfg(session.getTarget());
        if (start) {
            config.setStartLinePos1(selection[0]);
            config.setStartLinePos2(selection[1]);
            config.setStartSpawnPoint(player.getLocation().clone());
        } else {
            config.setFinishLinePos1(selection[0]);
            config.setFinishLinePos2(selection[1]);
        }
        session.markDirty();
        if (start) {
            Utils.sendAdminInfo(player, "请输入起点线之后的摔落高度；留空保留当前值 "
                    + config.getStartFallY() + "。");
            AnvilInputGui.openInteger(player, "起点线摔落高度", config.getStartFallY(), value -> {
                config.setStartFallY(value);
                session.markDirty();
                Utils.sendAdminSuccess(player, "已设置起点线之后的摔落高度为 " + value + "。");
            });
        }
        return Utils.formatAdminSuccess(start ? "已设置起点线。" : "已设置终点线。");
    }
}

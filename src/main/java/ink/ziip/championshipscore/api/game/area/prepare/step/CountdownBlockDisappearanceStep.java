package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.area.prepare.gui.CountdownBlockDisappearanceGui;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.instance.CountdownBlockDisappearance;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/** Optional editor control for the three-second block disappearance at the final countdown. */
public final class CountdownBlockDisappearanceStep extends PrepareStep {
    public enum Mode {
        RANDOM("RANDOM", "随机离散型", Material.SAND),
        DOOR_EAST_WEST("DOOR_EAST_WEST", "开门式（东西）", Material.OAK_DOOR),
        DOOR_NORTH_SOUTH("DOOR_NORTH_SOUTH", "开门式（南北）", Material.SPRUCE_DOOR),
        DOOR_VERTICAL("DOOR_VERTICAL", "开门式（竖直）", Material.IRON_BARS),
        DIRECT("DIRECT", "直接消失", Material.TNT);

        private final String value;
        private final String displayName;
        private final Material icon;

        Mode(String value, String displayName, Material icon) {
            this.value = value;
            this.displayName = displayName;
            this.icon = icon;
        }

        public String value() { return value; }
        public String displayName() { return displayName; }
        public Material icon() { return icon; }

        public static Mode from(String value) {
            if ("DOOR_HORIZONTAL".equalsIgnoreCase(value)) return DOOR_EAST_WEST;
            if (value != null) {
                for (Mode mode : values()) if (mode.value.equalsIgnoreCase(value)) return mode;
            }
            return RANDOM;
        }
    }

    public CountdownBlockDisappearanceStep() {
        super("countdown_block_disappearance", Component.text("开赛倒计时方块消失（可选）"),
                Component.text("设置选区与 3 秒内的消失方式；留空则不启用"),
                Material.CLOCK, StepCaptureType.SELECT);
    }

    @Override
    public boolean requiresWorldEdit() {
        return true;
    }

    @Override
    public boolean isSet(PrepareSession session) {
        // This is an explicitly optional prepare setting.
        return session != null;
    }

    @Override
    public String stateText(PrepareSession session) {
        if (session == null) return "可选";
        BaseGameConfig config = session.getTarget().config();
        if (!config.hasCountdownBlockDisappearance()) return "未启用（可选）";
        return "已启用 · " + Mode.from(config.getCountdownBlockDisappearanceMode()).displayName();
    }

    @Override
    public void openSelection(@NotNull PrepareSessionManager manager, @NotNull Player player,
                              @NotNull PrepareSession session) {
        CountdownBlockDisappearanceGui.open(player, session, this);
    }

    public String captureSelection(@NotNull PrepareSession session, @NotNull Player player) {
        if (!session.getFlow().isInCorrectWorld(player, session.getTarget()))
            return Utils.formatAdminError("请先前往当前地图世界 " + session.getTarget().worldName());
        Vector[] selection;
        try {
            selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, true);
        } catch (Exception exception) {
            return Utils.formatAdminError("请先用 WorldEdit 选取两个端点。");
        }
        long volume = volume(selection[0], selection[1]);
        if (volume <= 0 || volume > CountdownBlockDisappearance.MAX_SELECTION_VOLUME) {
            return Utils.formatAdminError("选区体积必须在 1 到 "
                    + CountdownBlockDisappearance.MAX_SELECTION_VOLUME + " 个方块以内。");
        }
        session.getTarget().config().setCountdownBlockDisappearanceBounds(selection[0], selection[1]);
        session.markDirty();
        return Utils.formatAdminSuccess("已设置开赛倒计时方块消失选区（" + volume + " 个方块）。");
    }

    public String selectMode(@NotNull PrepareSession session, @NotNull Mode mode) {
        session.getTarget().config().setCountdownBlockDisappearanceMode(mode.value());
        session.markDirty();
        return Utils.formatAdminSuccess("已设置消失方式：" + mode.displayName() + "。");
    }

    public String clearSelection(@NotNull PrepareSession session) {
        session.getTarget().config().clearCountdownBlockDisappearanceBounds();
        session.markDirty();
        return Utils.formatAdminSuccess("已关闭开赛倒计时方块消失。");
    }

    private static long volume(Vector first, Vector second) {
        return ((long) Math.abs(first.getBlockX() - second.getBlockX()) + 1L)
                * ((long) Math.abs(first.getBlockY() - second.getBlockY()) + 1L)
                * ((long) Math.abs(first.getBlockZ() - second.getBlockZ()) + 1L);
    }
}

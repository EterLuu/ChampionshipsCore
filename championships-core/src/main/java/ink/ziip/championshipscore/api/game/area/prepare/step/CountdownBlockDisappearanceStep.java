package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

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
        RANDOM("RANDOM", GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-001"), Material.SAND),
        DOOR_EAST_WEST("DOOR_EAST_WEST", GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-002"), Material.OAK_DOOR),
        DOOR_NORTH_SOUTH("DOOR_NORTH_SOUTH", GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-003"), Material.SPRUCE_DOOR),
        DOOR_VERTICAL("DOOR_VERTICAL", GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-004"), Material.IRON_BARS),
        DIRECT("DIRECT", GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-005"), Material.TNT);

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
        super("countdown_block_disappearance", Component.text(GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-006")),
                Component.text(GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-007")),
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
        if (session == null) return GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-008");
        BaseGameConfig config = session.getTarget().config();
        if (!config.hasCountdownBlockDisappearance()) return GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-009");
        return GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-010") + Mode.from(config.getCountdownBlockDisappearanceMode()).displayName();
    }

    @Override
    public void openSelection(@NotNull PrepareSessionManager manager, @NotNull Player player,
                              @NotNull PrepareSession session) {
        CountdownBlockDisappearanceGui.open(player, session, this);
    }

    public String captureSelection(@NotNull PrepareSession session, @NotNull Player player) {
        if (!session.getFlow().isInCorrectWorld(player, session.getTarget()))
            return Utils.formatAdminError(GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-011") + session.getTarget().worldName());
        Vector[] selection;
        try {
            selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, true);
        } catch (Exception exception) {
            return Utils.formatAdminError(GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-012"));
        }
        long volume = volume(selection[0], selection[1]);
        if (volume <= 0 || volume > CountdownBlockDisappearance.MAX_SELECTION_VOLUME) {
            return Utils.formatAdminError(GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-013")
                    + CountdownBlockDisappearance.MAX_SELECTION_VOLUME + GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-014"));
        }
        session.getTarget().config().setCountdownBlockDisappearanceBounds(selection[0], selection[1]);
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-015") + volume + GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-016"));
    }

    public String selectMode(@NotNull PrepareSession session, @NotNull Mode mode) {
        session.getTarget().config().setCountdownBlockDisappearanceMode(mode.value());
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-017") + mode.displayName() + "。");
    }

    public String clearSelection(@NotNull PrepareSession session) {
        session.getTarget().config().clearCountdownBlockDisappearanceBounds();
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("prepare-step-countdownblockdisappearancestep.text-018"));
    }

    private static long volume(Vector first, Vector second) {
        return ((long) Math.abs(first.getBlockX() - second.getBlockX()) + 1L)
                * ((long) Math.abs(first.getBlockY() - second.getBlockY()) + 1L)
                * ((long) Math.abs(first.getBlockZ() - second.getBlockZ()) + 1L);
    }
}

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
        RANDOM("RANDOM", GuiConfig.text("map-editor.steps.countdown-blocks.random-discrete-type"), Material.SAND),
        DOOR_EAST_WEST("DOOR_EAST_WEST", GuiConfig.text("map-editor.steps.countdown-blocks.open-door-thing"), Material.OAK_DOOR),
        DOOR_NORTH_SOUTH("DOOR_NORTH_SOUTH", GuiConfig.text("map-editor.steps.countdown-blocks.open-door-type-north-and-south"), Material.SPRUCE_DOOR),
        DOOR_VERTICAL("DOOR_VERTICAL", GuiConfig.text("map-editor.steps.countdown-blocks.open-door-type-vertical"), Material.IRON_BARS),
        DIRECT("DIRECT", GuiConfig.text("map-editor.steps.countdown-blocks.disappear-directly"), Material.TNT);

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
        super("countdown_block_disappearance", Component.text(GuiConfig.text("map-editor.steps.countdown-blocks.the-countdown-block-for-the-start-of-the-game-disappears-optional")),
                Component.text(GuiConfig.text("map-editor.steps.countdown-blocks.set-the-selection-and-how-to-disappear-within-3-seconds-leave-it-blank-to-disable-it")),
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
        if (session == null) return GuiConfig.text("map-editor.steps.countdown-blocks.optional");
        BaseGameConfig config = session.getTarget().config();
        if (!config.hasCountdownBlockDisappearance()) return GuiConfig.text("map-editor.steps.countdown-blocks.not-enabled-optional");
        return GuiConfig.text("map-editor.steps.countdown-blocks.enabled") + Mode.from(config.getCountdownBlockDisappearanceMode()).displayName();
    }

    @Override
    public void openSelection(@NotNull PrepareSessionManager manager, @NotNull Player player,
                              @NotNull PrepareSession session) {
        CountdownBlockDisappearanceGui.open(player, session, this);
    }

    public String captureSelection(@NotNull PrepareSession session, @NotNull Player player) {
        if (!session.getFlow().isInCorrectWorld(player, session.getTarget()))
            return Utils.formatAdminError(GuiConfig.text("map-editor.copy.please-go-to-the-current-map-world-first") + session.getTarget().worldName());
        Vector[] selection;
        try {
            selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, true);
        } catch (Exception exception) {
            return Utils.formatAdminError(GuiConfig.text("map-editor.copy.please-use-worldedit-to-select-two-endpoints-first"));
        }
        long volume = volume(selection[0], selection[1]);
        if (volume <= 0 || volume > CountdownBlockDisappearance.MAX_SELECTION_VOLUME) {
            return Utils.formatAdminError(GuiConfig.text("map-editor.steps.countdown-blocks.the-selection-volume-must-be-between-1-and")
                    + CountdownBlockDisappearance.MAX_SELECTION_VOLUME + GuiConfig.text("map-editor.copy.within-blocks"));
        }
        session.getTarget().config().setCountdownBlockDisappearanceBounds(selection[0], selection[1]);
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.steps.countdown-blocks.the-start-countdown-box-has-been-set-to-disappear-from-the-selection-area") + volume + GuiConfig.text("map-editor.steps.countdown-blocks.blocks"));
    }

    public String selectMode(@NotNull PrepareSession session, @NotNull Mode mode) {
        session.getTarget().config().setCountdownBlockDisappearanceMode(mode.value());
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.steps.countdown-blocks.disappearance-method-set") + mode.displayName() + "。");
    }

    public String clearSelection(@NotNull PrepareSession session) {
        session.getTarget().config().clearCountdownBlockDisappearanceBounds();
        session.markDirty();
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.steps.countdown-blocks.closed-start-countdown-box-disappears"));
    }

    private static long volume(Vector first, Vector second) {
        return ((long) Math.abs(first.getBlockX() - second.getBlockX()) + 1L)
                * ((long) Math.abs(first.getBlockY() - second.getBlockY()) + 1L)
                * ((long) Math.abs(first.getBlockZ() - second.getBlockZ()) + 1L);
    }
}

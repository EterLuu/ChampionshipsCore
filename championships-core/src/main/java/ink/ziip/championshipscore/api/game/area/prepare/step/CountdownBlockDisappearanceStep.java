package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

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

import java.util.Map;

/** Optional editor control for the three-second block disappearance at the final countdown. */
public final class CountdownBlockDisappearanceStep extends PrepareStep {
    public enum Mode {
        RANDOM("RANDOM", GuiConfig.text("map-editor.menus.countdown-blocks.items.random.title"), Material.SAND),
        DOOR_EAST_WEST("DOOR_EAST_WEST", GuiConfig.text("map-editor.menus.countdown-blocks.items.east-west.title"), Material.OAK_DOOR),
        DOOR_NORTH_SOUTH("DOOR_NORTH_SOUTH", GuiConfig.text("map-editor.menus.countdown-blocks.items.north-south.title"), Material.SPRUCE_DOOR),
        DOOR_VERTICAL("DOOR_VERTICAL", GuiConfig.text("map-editor.menus.countdown-blocks.items.vertical.title"), Material.IRON_BARS),
        DIRECT("DIRECT", GuiConfig.text("map-editor.menus.countdown-blocks.items.direct.title"), Material.TNT);

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
        super("countdown_block_disappearance", Component.text(GuiConfig.text("map-editor.menus.step-list.items.countdown-blocks.title")),
                Component.text(GuiConfig.line("map-editor.menus.step-list.items.countdown-blocks.lore", 0)),
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
        if (session == null) return GuiConfig.text("map-editor.menus.step-list.items.countdown-blocks.states.optional.title");
        BaseGameConfig config = session.getTarget().config();
        if (!config.hasCountdownBlockDisappearance()) return GuiConfig.text("map-editor.menus.step-list.items.countdown-blocks.states.disabled.title");
        return GuiConfig.text("map-editor.menus.step-list.items.countdown-blocks.states.enabled.title",
                Map.of("mode", Mode.from(config.getCountdownBlockDisappearanceMode()).displayName()));
    }

    @Override
    public void openSelection(@NotNull PrepareSessionManager manager, @NotNull Player player,
                              @NotNull PrepareSession session) {
        CountdownBlockDisappearanceGui.open(player, session, this);
    }

    public String captureSelection(@NotNull PrepareSession session, @NotNull Player player) {
        if (!session.getFlow().isInCorrectWorld(player, session.getTarget()))
            return Utils.formatAdminError(MessageConfig.MAP_EDITOR_STEP_GO_TO_MAP_WORLD_FIRST.replace("%world%", session.getTarget().worldName()));
        Vector[] selection;
        try {
            selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, true);
        } catch (Exception exception) {
            return Utils.formatAdminError(MessageConfig.MAP_EDITOR_STEP_SELECT_TWO_WORLDEDIT_ENDPOINTS);
        }
        long volume = volume(selection[0], selection[1]);
        if (volume <= 0 || volume > CountdownBlockDisappearance.MAX_SELECTION_VOLUME) {
            return Utils.formatAdminError(MessageConfig.MAP_EDITOR_STEP_COUNTDOWN_BLOCKS_VOLUME_BETWEEN.replace("%max%", String.valueOf(CountdownBlockDisappearance.MAX_SELECTION_VOLUME)));
        }
        session.getTarget().config().setCountdownBlockDisappearanceBounds(selection[0], selection[1]);
        session.markDirty();
        return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_STEP_COUNTDOWN_BLOCKS_SET.replace("%volume%", String.valueOf(volume)));
    }

    public String selectMode(@NotNull PrepareSession session, @NotNull Mode mode) {
        session.getTarget().config().setCountdownBlockDisappearanceMode(mode.value());
        session.markDirty();
        return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_STEP_COUNTDOWN_BLOCKS_MODE_SET.replace("%mode%", mode.displayName()));
    }

    public String clearSelection(@NotNull PrepareSession session) {
        session.getTarget().config().clearCountdownBlockDisappearanceBounds();
        session.markDirty();
        return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_STEP_COUNTDOWN_BLOCKS_CLOSED);
    }

    private static long volume(Vector first, Vector second) {
        return ((long) Math.abs(first.getBlockX() - second.getBlockX()) + 1L)
                * ((long) Math.abs(first.getBlockY() - second.getBlockY()) + 1L)
                * ((long) Math.abs(first.getBlockZ() - second.getBlockZ()) + 1L);
    }
}

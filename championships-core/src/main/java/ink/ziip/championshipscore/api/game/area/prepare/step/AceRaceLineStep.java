package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

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
                Component.text(start ? GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.start-line.title") : GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.finish-line.title")),
                Component.text(GuiConfig.line("map-editor.menus.step-list.games.ace-race.items.start-line.lore", 0)
                        + (start ? GuiConfig.line("map-editor.menus.step-list.games.ace-race.items.start-line.lore", 1) : GuiConfig.line("map-editor.menus.step-list.games.ace-race.items.finish-line.lore", 1))),
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
            return Utils.formatAdminError(MessageConfig.MAP_EDITOR_ACE_LINE_SELECT_FIRST);
        }
        int spanX = Math.abs(selection[0].getBlockX() - selection[1].getBlockX());
        int spanY = Math.abs(selection[0].getBlockY() - selection[1].getBlockY());
        int spanZ = Math.abs(selection[0].getBlockZ() - selection[1].getBlockZ());
        if (spanY != 0 || (spanX > 0 && spanZ > 0) || (spanX == 0 && spanZ == 0))
            return Utils.formatAdminError(MessageConfig.MAP_EDITOR_ACE_LINE_INVALID);
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
            Utils.sendAdminInfo(player, MessageConfig.MAP_EDITOR_ACE_START_FALL_INPUT.replace("%fall%", String.valueOf(config.getStartFallY())));
            AnvilInputGui.openInteger(player, GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.start-fall-height.title"), config.getStartFallY(), value -> {
                config.setStartFallY(value);
                session.markDirty();
                Utils.sendAdminSuccess(player, MessageConfig.MAP_EDITOR_ACE_START_FALL_SET.replace("%fall%", String.valueOf(value)));
            });
        }
        return Utils.formatAdminSuccess(start ? MessageConfig.MAP_EDITOR_ACE_START_LINE_SET : MessageConfig.MAP_EDITOR_ACE_FINISH_LINE_SET);
    }
}

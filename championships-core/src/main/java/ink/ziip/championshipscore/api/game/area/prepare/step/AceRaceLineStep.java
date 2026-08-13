package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

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
                Component.text(start ? GuiConfig.text("map-editor.games.ace-race.steps.start-finish-lines.starting-line") : GuiConfig.text("map-editor.games.ace-race.steps.start-finish-lines.finish-line")),
                Component.text(GuiConfig.text("map-editor.games.ace-race.steps.start-finish-lines.race-line-selection-hint")
                        + (start ? GuiConfig.text("map-editor.games.ace-race.steps.start-finish-lines.the-current-location-also-serves-as-the-starting-point-of-birth") : GuiConfig.text("map-editor.games.ace-race.steps.start-finish-lines.used-for-lap-counting-finishing"))),
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
            return Utils.formatAdminError(GuiConfig.text("map-editor.games.ace-race.steps.start-finish-lines.please-use-worldedit-to-select-the-starting-line-or-finishing-line-first"));
        }
        int spanX = Math.abs(selection[0].getBlockX() - selection[1].getBlockX());
        int spanY = Math.abs(selection[0].getBlockY() - selection[1].getBlockY());
        int spanZ = Math.abs(selection[0].getBlockZ() - selection[1].getBlockZ());
        if (spanY != 0 || (spanX > 0 && spanZ > 0) || (spanX == 0 && spanZ == 0))
            return Utils.formatAdminError(GuiConfig.text("map-editor.games.ace-race.steps.start-finish-lines.start-line-finish-line-must-be-the-same-height-a-straight-line-extending-in-the-x-or-z-direction"));
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
            Utils.sendAdminInfo(player, GuiConfig.text("map-editor.games.ace-race.steps.start-finish-lines.please-enter-the-fall-height-after-the-starting-line-leave-blank-to-retain-the-current-value")
                    + config.getStartFallY() + "。");
            AnvilInputGui.openInteger(player, GuiConfig.text("map-editor.games.ace-race.steps.start-finish-lines.starting-line-fall-height"), config.getStartFallY(), value -> {
                config.setStartFallY(value);
                session.markDirty();
                Utils.sendAdminSuccess(player, GuiConfig.text("map-editor.games.ace-race.steps.start-finish-lines.the-fall-height-after-the-start-line-has-been-set-is") + value + "。");
            });
        }
        return Utils.formatAdminSuccess(start ? GuiConfig.text("map-editor.games.ace-race.steps.start-finish-lines.start-line-set") : GuiConfig.text("map-editor.games.ace-race.steps.start-finish-lines.finish-line-set"));
    }
}

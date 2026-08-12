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
                Component.text(start ? GuiConfig.text("prepare-step-aceracelinestep.text-001") : GuiConfig.text("prepare-step-aceracelinestep.text-002")),
                Component.text(GuiConfig.text("prepare-step-aceracelinestep.text-003")
                        + (start ? GuiConfig.text("prepare-step-aceracelinestep.text-004") : GuiConfig.text("prepare-step-aceracelinestep.text-005"))),
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
            return Utils.formatAdminError(GuiConfig.text("prepare-step-aceracelinestep.text-006"));
        }
        int spanX = Math.abs(selection[0].getBlockX() - selection[1].getBlockX());
        int spanY = Math.abs(selection[0].getBlockY() - selection[1].getBlockY());
        int spanZ = Math.abs(selection[0].getBlockZ() - selection[1].getBlockZ());
        if (spanY != 0 || (spanX > 0 && spanZ > 0) || (spanX == 0 && spanZ == 0))
            return Utils.formatAdminError(GuiConfig.text("prepare-step-aceracelinestep.text-007"));
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
            Utils.sendAdminInfo(player, GuiConfig.text("prepare-step-aceracelinestep.text-008")
                    + config.getStartFallY() + "。");
            AnvilInputGui.openInteger(player, GuiConfig.text("prepare-step-aceracelinestep.text-009"), config.getStartFallY(), value -> {
                config.setStartFallY(value);
                session.markDirty();
                Utils.sendAdminSuccess(player, GuiConfig.text("prepare-step-aceracelinestep.text-010") + value + "。");
            });
        }
        return Utils.formatAdminSuccess(start ? GuiConfig.text("prepare-step-aceracelinestep.text-011") : GuiConfig.text("prepare-step-aceracelinestep.text-012"));
    }
}

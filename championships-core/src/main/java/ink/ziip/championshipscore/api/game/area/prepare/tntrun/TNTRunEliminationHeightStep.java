package ink.ziip.championshipscore.api.game.area.prepare.tntrun;

import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AnvilInputGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.StepMenuGui;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunConfig;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** Inputs an absolute fall-elimination Y without requiring the editor to fly into the void. */
final class TNTRunEliminationHeightStep extends PrepareStep {
    private static final String PATH = "map-editor.menus.step-list.games.tnt-run.items.elimination-height";

    TNTRunEliminationHeightStep() {
        super("elimination_height", LegacyText.component(GuiConfig.text(PATH + ".title")),
                LegacyText.component(GuiConfig.line(PATH + ".lore", 0)), Material.BEDROCK, StepCaptureType.SELECT);
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null && config(session).getEliminationY() != null;
    }

    @Override
    public String stateText(PrepareSession session) {
        if (session == null) return null;
        TNTRunConfig config = config(session);
        return GuiConfig.text(PATH + ".states." + (isSet(session) ? "set" : "unset") + ".title",
                Map.of("height", config.getEliminationY() == null
                        ? config.getDefaultEliminationY() : config.getEliminationY()));
    }

    @Override
    public void openSelection(@NotNull PrepareSessionManager manager, @NotNull Player player,
                              @NotNull PrepareSession session) {
        TNTRunConfig config = config(session);
        double current = config.getEliminationY() == null ? config.getDefaultEliminationY() : config.getEliminationY();
        Runnable back = () -> {
            if (manager.getSession(player) == session) StepMenuGui.open(player, session);
        };
        AnvilInputGui.openEditorText(player, GuiConfig.text(PATH + ".title"), String.valueOf(current),
                text -> {
                    try {
                        if (Double.isFinite(Double.parseDouble(text))) return null;
                    } catch (NumberFormatException ignored) {
                    }
                    return MessageConfig.MAP_EDITOR_TNT_ELIMINATION_HEIGHT_INVALID;
                }, text -> {
                    if (manager.getSession(player) != session || !session.getTarget().canSaveMap()) return;
                    config.setEliminationY(Double.parseDouble(text));
                    session.markDirty();
                    Utils.sendAdminSuccess(player, MessageConfig.MAP_EDITOR_TNT_ELIMINATION_HEIGHT_SET
                            .replace("%height%", String.valueOf(config.getEliminationY())));
                    back.run();
                }, back);
    }

    private static TNTRunConfig config(PrepareSession session) {
        return (TNTRunConfig) session.getTarget().config();
    }
}

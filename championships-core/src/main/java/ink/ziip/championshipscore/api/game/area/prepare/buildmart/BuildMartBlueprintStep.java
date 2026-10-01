package ink.ziip.championshipscore.api.game.area.prepare.buildmart;

import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.area.prepare.gui.BuildMartBlueprintGui;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class BuildMartBlueprintStep extends PrepareStep {
    public BuildMartBlueprintStep() {
        super("blueprints", LegacyText.component(GuiConfig.text("map-editor.games.build-mart.menus.blueprints.steps.title")),
                LegacyText.component(GuiConfig.line("map-editor.games.build-mart.menus.blueprints.steps.lore", 0)),
                Material.BRICKS, StepCaptureType.SELECT);
    }
    @Override public boolean isSet(PrepareSession session) { return true; }
    @Override public void openSelection(PrepareSessionManager manager, Player player, PrepareSession session) {
        BuildMartBlueprintGui.open(manager, player, session, 0);
    }
}

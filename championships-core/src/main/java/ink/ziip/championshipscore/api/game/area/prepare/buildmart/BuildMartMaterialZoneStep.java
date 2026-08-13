package ink.ziip.championshipscore.api.game.area.prepare.buildmart;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.area.prepare.gui.BuildMartMaterialZoneGui;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Repeated Build Mart resource-area editor: each WorldEdit cuboid retains its original block snapshot. */
public final class BuildMartMaterialZoneStep extends PrepareStep {
    public BuildMartMaterialZoneStep() {
        super("material_zones", Component.text(GuiConfig.text("map-editor.games.build-mart.steps.material-zones.material-area")),
                Component.text(GuiConfig.text("map-editor.games.build-mart.steps.material-zones.save-the-material-area-selection-as-it-is-restored-regularly-during-the-game")), Material.CHEST, StepCaptureType.SELECT);
    }

    @Override
    public boolean isSet(PrepareSession session) {
        // Material areas are optional: maps without them retain their existing resource layout.
        return session != null;
    }

    @Override
    public String stateText(PrepareSession session) {
        if (session == null) return null;
        int count = config(session.getTarget()).getMaterialZones().size();
        return count == 0 ? GuiConfig.text("map-editor.games.build-mart.steps.material-zones.not-set-optional") : GuiConfig.text("map-editor.games.build-mart.steps.material-zones.already-set") + count + GuiConfig.text("map-editor.games.build-mart.steps.material-zones.material-zone-count-suffix");
    }

    @Override
    public void openSelection(@NotNull PrepareSessionManager manager, @NotNull Player player,
                              @NotNull PrepareSession session) {
        BuildMartMaterialZoneGui.open(manager, player, session, this);
    }

    private static BuildMartConfig config(SetupTarget target) {
        return (BuildMartConfig) target.config();
    }
}

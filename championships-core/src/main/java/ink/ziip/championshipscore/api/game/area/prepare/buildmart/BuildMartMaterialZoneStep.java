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
        super("material_zones", Component.text(GuiConfig.text("prepare-buildmart-buildmartmaterialzonestep.text-001")),
                Component.text(GuiConfig.text("prepare-buildmart-buildmartmaterialzonestep.text-002")), Material.CHEST, StepCaptureType.SELECT);
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
        return count == 0 ? GuiConfig.text("prepare-buildmart-buildmartmaterialzonestep.text-003") : GuiConfig.text("prepare-buildmart-buildmartmaterialzonestep.text-004") + count + GuiConfig.text("prepare-buildmart-buildmartmaterialzonestep.text-005");
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

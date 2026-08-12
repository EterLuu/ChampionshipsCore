package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.acerace.AceRaceArea;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Session-only Ace Race course preview showing respawn crystals and progress-line particles. */
public final class AceRaceMapPreviewStep extends PrepareStep {
    public AceRaceMapPreviewStep() {
        super("map_preview", Component.text(GuiConfig.text("prepare-step-aceracemappreviewstep.text-001")),
                Component.text(GuiConfig.text("prepare-step-aceracemappreviewstep.text-002")),
                Material.END_CRYSTAL, StepCaptureType.TOGGLE);
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null;
    }

    @Override
    public String stateText(PrepareSession session) {
        AceRaceArea area = area(session);
        return area != null && area.isMapEditPreviewEnabled() ? GuiConfig.text("prepare-step-aceracemappreviewstep.text-003") : GuiConfig.text("prepare-step-aceracemappreviewstep.text-004");
    }

    @Override
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        AceRaceArea area = area(session);
        if (area == null) return GuiConfig.text("prepare-step-aceracemappreviewstep.text-005");
        boolean enabled = area.toggleMapEditPreview(player);
        return enabled ? GuiConfig.text("prepare-step-aceracemappreviewstep.text-006") : GuiConfig.text("prepare-step-aceracemappreviewstep.text-007");
    }

    private static AceRaceArea area(PrepareSession session) {
        if (session == null) return null;
        SetupTarget target = session.getTarget();
        return target.plugin().getGameManager().getAceRaceManager().getArea(target.name());
    }
}

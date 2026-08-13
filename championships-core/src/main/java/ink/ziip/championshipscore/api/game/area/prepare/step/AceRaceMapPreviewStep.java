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
        super("map_preview", Component.text(GuiConfig.text("map-editor.games.ace-race.steps.map-preview.show-track-point-preview")),
                Component.text(GuiConfig.text("map-editor.games.ace-race.steps.map-preview.preview-display-and-cleanup-hint")),
                Material.END_CRYSTAL, StepCaptureType.TOGGLE);
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null;
    }

    @Override
    public String stateText(PrepareSession session) {
        AceRaceArea area = area(session);
        return area != null && area.isMapEditPreviewEnabled() ? GuiConfig.text("map-editor.games.ace-race.steps.map-preview.currently-turned-on") : GuiConfig.text("map-editor.games.ace-race.steps.map-preview.currently-closed");
    }

    @Override
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        AceRaceArea area = area(session);
        if (area == null) return GuiConfig.text("map-editor.games.ace-race.steps.map-preview.unable-to-find-current-ace-race-map-instance");
        boolean enabled = area.toggleMapEditPreview(player);
        return enabled ? GuiConfig.text("map-editor.games.ace-race.steps.map-preview.track-point-preview-has-been-turned-on-the-crystal-can-right-click-to-edit-the-respawn-point") : GuiConfig.text("map-editor.games.ace-race.steps.map-preview.track-point-preview-has-been-closed");
    }

    private static AceRaceArea area(PrepareSession session) {
        if (session == null) return null;
        SetupTarget target = session.getTarget();
        return target.plugin().getGameManager().getAceRaceManager().getArea(target.name());
    }
}

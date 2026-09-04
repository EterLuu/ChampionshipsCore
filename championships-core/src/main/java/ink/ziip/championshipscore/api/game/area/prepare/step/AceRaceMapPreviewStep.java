package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

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
        super("map_preview", Component.text(GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.map-preview.title")),
                Component.text(GuiConfig.line("map-editor.menus.step-list.games.ace-race.items.map-preview.lore", 0)),
                Material.END_CRYSTAL, StepCaptureType.TOGGLE);
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null;
    }

    @Override
    public String stateText(PrepareSession session) {
        AceRaceArea area = area(session);
        return area != null && area.isMapEditPreviewEnabled() ? GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.map-preview.states.enabled.title") : GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.map-preview.states.disabled.title");
    }

    @Override
    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
        AceRaceArea area = area(session);
        if (area == null) return MessageConfig.MAP_EDITOR_ACE_PREVIEW_AREA_MISSING;
        boolean enabled = area.toggleMapEditPreview(player);
        return enabled ? MessageConfig.MAP_EDITOR_ACE_PREVIEW_ENABLED : MessageConfig.MAP_EDITOR_ACE_PREVIEW_DISABLED;
    }

    private static AceRaceArea area(PrepareSession session) {
        if (session == null) return null;
        SetupTarget target = session.getTarget();
        return target.plugin().getGameManager().getAceRaceManager().getArea(target.name());
    }
}

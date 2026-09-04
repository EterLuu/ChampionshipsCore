package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import ink.ziip.championshipscore.api.game.acerace.AceRaceConfig;
import ink.ziip.championshipscore.api.game.acerace.AceRaceArea;
import ink.ziip.championshipscore.api.game.area.prepare.step.AceRaceProgressPointListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.AceRaceRespawnPointListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.AceRaceMapPreviewStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.AceRaceLineStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Guided setup for one course region in Ace Race's shared permanent world. */
public final class AceRacePrepareFlow extends PrepareFlowDefinition {
    @Override public @NotNull String worldName(@NotNull SetupTarget target) { return target.worldName(); }
    @Override public boolean isInCorrectWorld(@NotNull Player player, @NotNull SetupTarget target) {
        return target.worldName().equals(player.getWorld().getName());
    }
    @Override public @NotNull Location copyZeroLocation(@NotNull SetupTarget target) {
        AceRaceConfig config = cfg(target);
        if (config.getStartSpawnPoint() != null) return config.getStartSpawnPoint();
        World world = Bukkit.getWorld(target.worldName());
        return world == null ? CCConfig.LOBBY_LOCATION : world.getSpawnLocation();
    }
    @Override public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        return List.of(
                new ConfirmWorldStep(player -> isInCorrectWorld(player, target), target.worldName()),
                new WeSelectionStep("area_pos", Component.text(GuiConfig.text("map-editor.menus.step-list.items.track-boundary.title")),
                        Component.text(GuiConfig.line("map-editor.menus.step-list.items.track-boundary.lore", 0)), Material.BEDROCK,
                        t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                        (t, value) -> {
                            cfg(t).setAreaPos1(value[0]);
                            cfg(t).setAreaPos2(value[1]);
                        }, Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_STEP_TRACK_BOUNDARY_SET)),
                new StandAndRunStep("spectator_spawn", Component.text(GuiConfig.text("map-editor.menus.step-list.items.spectator-spawn.title")), Component.text(GuiConfig.line("map-editor.menus.step-list.items.spectator-spawn.lore", 0)),
                        Material.ENDER_EYE, t -> cfg(t).getSpectatorSpawnPoint() != null,
                        (t, value) -> cfg(t).setSpectatorSpawnPoint(value), Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_STEP_SPECTATOR_SPAWN_POINT_SET)),
                new AceRaceLineStep(true),
                new AceRaceLineStep(false),
                new AceRaceProgressPointListStep(),
                new AceRaceRespawnPointListStep(),
                new AceRaceMapPreviewStep());
    }

    @Override
    public void onSessionExit(@NotNull PrepareSession session) {
        AceRaceArea area = session.getPlugin().getGameManager().getAceRaceManager().getArea(session.getAreaName());
        if (area != null) area.disableMapEditPreview();
    }
    private static AceRaceConfig cfg(SetupTarget target) { return (AceRaceConfig) target.config(); }
}

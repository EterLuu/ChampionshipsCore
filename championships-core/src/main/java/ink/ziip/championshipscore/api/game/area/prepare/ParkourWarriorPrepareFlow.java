package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.step.CheckpointListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorConfig;
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

/** Guided setup for Parkour Warrior's shared permanent world. */
public final class ParkourWarriorPrepareFlow extends PrepareFlowDefinition {
    @Override public @NotNull String worldName(@NotNull SetupTarget target) { return target.worldName(); }
    @Override public boolean isInCorrectWorld(@NotNull Player player, @NotNull SetupTarget target) {
        return target.worldName().equals(player.getWorld().getName());
    }
    @Override public @NotNull Location copyZeroLocation(@NotNull SetupTarget target) {
        ParkourWarriorConfig config = cfg(target);
        if (config.getSpectatorSpawnPoint() != null) return config.getSpectatorSpawnPoint();
        World world = Bukkit.getWorld(target.worldName());
        return world == null ? CCConfig.LOBBY_LOCATION : world.getSpawnLocation();
    }
    @Override public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        return List.of(
                new ConfirmWorldStep(player -> isInCorrectWorld(player, target), target.worldName()),
                new WeSelectionStep("area_pos", Component.text(GuiConfig.text("map-editor.copy.track-boundary")), Component.text(GuiConfig.text("map-editor.games.parkour-warrior.setup.use-worldedit-to-select-the-complete-area-of-the-parkour-track")),
                        Material.BEDROCK, t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                        (t, v) -> { cfg(t).setAreaPos1(v[0]); cfg(t).setAreaPos2(v[1]); }, Utils.formatAdminSuccess(GuiConfig.text("map-editor.copy.track-boundaries-set"))),
                new StandAndRunStep("spectator_spawn", Component.text(GuiConfig.text("map-editor.copy.spectator-spawn-point")), Component.text(GuiConfig.text("map-editor.copy.stand-in-a-spectator-position-and-click")),
                        Material.ENDER_EYE, t -> cfg(t).getSpectatorSpawnPoint() != null,
                        (t, l) -> cfg(t).setSpectatorSpawnPoint(l), Utils.formatAdminSuccess(GuiConfig.text("map-editor.copy.spectator-spawn-point-has-been-set"))),
                new StandAndRunStep("player_spawn", Component.text(GuiConfig.text("map-editor.copy.player-spawn-point")), Component.text(GuiConfig.text("map-editor.games.parkour-warrior.setup.after-standing-at-the-starting-point-of-the-track-click")),
                        Material.PLAYER_HEAD, t -> cfg(t).getPlayerSpawnPoint() != null,
                        (t, l) -> cfg(t).setPlayerSpawnPoint(l), Utils.formatAdminSuccess(GuiConfig.text("map-editor.copy.player-spawn-point-has-been-set"))),
                new CheckpointListStep());
    }
    private static ParkourWarriorConfig cfg(SetupTarget target) { return (ParkourWarriorConfig) target.config(); }
}

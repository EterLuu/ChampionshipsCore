package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyConfig;
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

/** Guided setup for a Hoty Cody Dusky region in its shared permanent world. */
public final class HotyCodyDuskyPrepareFlow extends PrepareFlowDefinition {
    @Override public @NotNull String worldName(@NotNull SetupTarget target) { return target.worldName(); }
    @Override public boolean isInCorrectWorld(@NotNull Player player, @NotNull SetupTarget target) {
        return target.worldName().equals(player.getWorld().getName());
    }
    @Override public @NotNull Location copyZeroLocation(@NotNull SetupTarget target) {
        HotyCodyDuskyConfig config = cfg(target);
        if (config.getSpectatorSpawnPoint() != null) return config.getSpectatorSpawnPoint();
        World world = Bukkit.getWorld(target.worldName());
        return world == null ? CCConfig.LOBBY_LOCATION : world.getSpawnLocation();
    }
    @Override public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        return List.of(
                new ConfirmWorldStep(player -> isInCorrectWorld(player, target), target.worldName()),
                new WeSelectionStep("area_pos", Component.text(GuiConfig.text("map-editor.copy.site-boundaries")), Component.text(GuiConfig.text("map-editor.copy.use-worldedit-to-select-the-complete-game-area")),
                        Material.BEDROCK, t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                        (t, v) -> { cfg(t).setAreaPos1(v[0]); cfg(t).setAreaPos2(v[1]); }, Utils.formatAdminSuccess(GuiConfig.text("map-editor.copy.site-boundaries-set"))),
                new StandAndRunStep("spectator_spawn", Component.text(GuiConfig.text("map-editor.copy.spectator-spawn-point")), Component.text(GuiConfig.text("map-editor.copy.stand-in-a-spectator-position-and-click")),
                        Material.ENDER_EYE, t -> cfg(t).getSpectatorSpawnPoint() != null,
                        (t, l) -> cfg(t).setSpectatorSpawnPoint(l), Utils.formatAdminSuccess(GuiConfig.text("map-editor.copy.spectator-spawn-point-has-been-set"))),
                new StandAndRunStep("player_spawn", Component.text(GuiConfig.text("map-editor.copy.player-spawn-point")), Component.text(GuiConfig.text("map-editor.games.hoty-cody-dusky.setup.click-after-standing-at-the-birth-position")),
                        Material.PLAYER_HEAD, t -> cfg(t).getPlayerSpawnPoint() != null,
                        (t, l) -> cfg(t).setPlayerSpawnPoint(l), Utils.formatAdminSuccess(GuiConfig.text("map-editor.copy.player-spawn-point-has-been-set"))));
    }
    private static HotyCodyDuskyConfig cfg(SetupTarget target) { return (HotyCodyDuskyConfig) target.config(); }
}

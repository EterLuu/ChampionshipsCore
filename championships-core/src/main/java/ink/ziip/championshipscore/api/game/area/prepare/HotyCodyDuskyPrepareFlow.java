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
                new WeSelectionStep("area_pos", Component.text(GuiConfig.text("area-prepare-hotycodyduskyprepareflow.text-001")), Component.text(GuiConfig.text("area-prepare-hotycodyduskyprepareflow.text-002")),
                        Material.BEDROCK, t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                        (t, v) -> { cfg(t).setAreaPos1(v[0]); cfg(t).setAreaPos2(v[1]); }, Utils.formatAdminSuccess(GuiConfig.text("area-prepare-hotycodyduskyprepareflow.text-003"))),
                new StandAndRunStep("spectator_spawn", Component.text(GuiConfig.text("area-prepare-hotycodyduskyprepareflow.text-004")), Component.text(GuiConfig.text("area-prepare-hotycodyduskyprepareflow.text-005")),
                        Material.ENDER_EYE, t -> cfg(t).getSpectatorSpawnPoint() != null,
                        (t, l) -> cfg(t).setSpectatorSpawnPoint(l), Utils.formatAdminSuccess(GuiConfig.text("area-prepare-hotycodyduskyprepareflow.text-006"))),
                new StandAndRunStep("player_spawn", Component.text(GuiConfig.text("area-prepare-hotycodyduskyprepareflow.text-007")), Component.text(GuiConfig.text("area-prepare-hotycodyduskyprepareflow.text-008")),
                        Material.PLAYER_HEAD, t -> cfg(t).getPlayerSpawnPoint() != null,
                        (t, l) -> cfg(t).setPlayerSpawnPoint(l), Utils.formatAdminSuccess(GuiConfig.text("area-prepare-hotycodyduskyprepareflow.text-009"))));
    }
    private static HotyCodyDuskyConfig cfg(SetupTarget target) { return (HotyCodyDuskyConfig) target.config(); }
}

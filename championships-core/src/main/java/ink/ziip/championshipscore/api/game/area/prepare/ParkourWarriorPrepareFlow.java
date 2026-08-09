package ink.ziip.championshipscore.api.game.area.prepare;

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
                new WeSelectionStep("area_pos", Component.text("赛道边界"), Component.text("用 WorldEdit 选取跑酷赛道完整区域"),
                        Material.BEDROCK, t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                        (t, v) -> { cfg(t).setAreaPos1(v[0]); cfg(t).setAreaPos2(v[1]); }, Utils.formatAdminSuccess("已设置赛道边界。")),
                new StandAndRunStep("spectator_spawn", Component.text("旁观者出生点"), Component.text("站到旁观位置后点击"),
                        Material.ENDER_EYE, t -> cfg(t).getSpectatorSpawnPoint() != null,
                        (t, l) -> cfg(t).setSpectatorSpawnPoint(l), Utils.formatAdminSuccess("已设置旁观者出生点。")),
                new StandAndRunStep("player_spawn", Component.text("玩家出生点"), Component.text("站到赛道起点后点击"),
                        Material.PLAYER_HEAD, t -> cfg(t).getPlayerSpawnPoint() != null,
                        (t, l) -> cfg(t).setPlayerSpawnPoint(l), Utils.formatAdminSuccess("已设置玩家出生点。")),
                new CheckpointListStep());
    }
    private static ParkourWarriorConfig cfg(SetupTarget target) { return (ParkourWarriorConfig) target.config(); }
}

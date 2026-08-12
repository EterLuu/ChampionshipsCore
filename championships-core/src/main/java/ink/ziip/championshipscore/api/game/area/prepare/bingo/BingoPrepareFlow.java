package ink.ziip.championshipscore.api.game.area.prepare.bingo;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareFlowDefinition;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.bingo.BingoConfig;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.world.WorldManager;
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

/**
 * Bingo's prepare flow. Bingo is the thinnest game (whole-world play, no schematic, no stamp, players
 * scattered): the only geometric point is the spectator spawn, so the flow is just confirm-world then set
 * that one point. The shared bingo world is created at startup by {@code WorldManager}, so any bingo
 * dimension counts as "the correct world".
 */
public class BingoPrepareFlow extends PrepareFlowDefinition {

    @Override
    public @NotNull String worldName(@NotNull SetupTarget target) {
        return WorldManager.BINGO_OVERWORLD;
    }

    @Override
    public boolean isInCorrectWorld(@NotNull Player player, @NotNull SetupTarget target) {
        return WorldManager.isBingoWorld(player.getWorld());
    }

    @Override
    public @NotNull Location copyZeroLocation(@NotNull SetupTarget target) {
        World overworld = Bukkit.getWorld(WorldManager.BINGO_OVERWORLD);
        if (overworld == null) return CCConfig.LOBBY_LOCATION;
        Location spectator = target.config().getSpectatorSpawnPoint();
        return spectator != null ? spectator : overworld.getSpawnLocation();
    }

    @Override
    public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        ConfirmWorldStep confirm = new ConfirmWorldStep(
                player -> WorldManager.isBingoWorld(player.getWorld()),
                WorldManager.BINGO_OVERWORLD, false);

        StandAndRunStep spectator = new StandAndRunStep(
                "spectator_spawn",
                Component.text(GuiConfig.text("prepare-bingo-bingoprepareflow.text-001")),
                Component.text(GuiConfig.text("prepare-bingo-bingoprepareflow.text-002")),
                Material.ENDER_EYE,
                t -> ((BingoConfig) t.config()).getSpectatorSpawnPoint() != null,
                (t, loc) -> ((BingoConfig) t.config()).setSpectatorSpawnPoint(loc),
                Utils.formatAdminSuccess(GuiConfig.text("prepare-bingo-bingoprepareflow.text-003")));

        return List.of(confirm, spectator);
    }
}

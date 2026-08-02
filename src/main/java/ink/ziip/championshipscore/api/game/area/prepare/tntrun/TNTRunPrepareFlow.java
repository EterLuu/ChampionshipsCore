package ink.ziip.championshipscore.api.game.area.prepare.tntrun;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareFlowDefinition;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.SchematicStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StampStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunConfig;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunLayout;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/** Unified prepare flow for TNT Run's one match with several load-balancing arena copies. */
public class TNTRunPrepareFlow extends PrepareFlowDefinition {
    @Override
    public @NotNull String worldName(@NotNull SetupTarget target) {
        return target.worldName();
    }

    @Override
    public boolean isInCorrectWorld(@NotNull Player player, @NotNull SetupTarget target) {
        return target.worldName().equals(player.getWorld().getName());
    }

    @Override
    public @NotNull Location copyZeroLocation(@NotNull SetupTarget target) {
        World world = Bukkit.getWorld(target.worldName());
        return world == null ? CCConfig.LOBBY_LOCATION : cfg(target).getCopyGrid().origin(0).toLocation(world);
    }

    @Override
    public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        File schematic = new File(new File(new File(new File(target.plugin().getDataFolder(),
                "tntrun"), "schematics"), target.name()), "arena.schem");
        return List.of(
                new ConfirmWorldStep(player -> isInCorrectWorld(player, target), target.worldName()),
                new SchematicStep(plugin -> schematic, Component.text("保存 0 号赛道模板"),
                        Component.text("选中完整单赛道后保存 arena.schem")),
                StampStep.adaptive(plugin -> schematic,
                        (t, size) -> cfg(t).prepareCopyGrid(size),
                        (t, count) -> cfg(t).setCopies(count),
                        (session, world) -> {
                            TNTRunConfig previous = cfg(session.getTarget());
                            ArenaPreparer.clearCopies(session.getPlugin(), world, previous.getCopyGrid(),
                                    previous.getCopies(), previous.getCopySize());
                        }),
                new StandAndRunStep("copy_spawn", Component.text("0 号赛道出生点"),
                        Component.text("站到 copy0 玩家出生点后点击"), Material.ELYTRA,
                        t -> cfg(t).getCopySpawn() != null, (t, loc) -> cfg(t).setCopySpawn(loc),
                        Utils.formatAdminSuccess("已设置 0 号赛道出生点。")),
                new StandAndRunStep("spectator_spawn", Component.text("旁观者出生点"),
                        Component.text("站到旁观位置后点击"), Material.ENDER_EYE,
                        t -> cfg(t).getSpectatorSpawnPoint() != null,
                        (t, loc) -> cfg(t).setSpectatorSpawnPoint(loc),
                        Utils.formatAdminSuccess("已设置旁观者出生点。"))
        );
    }

    @Override
    public @NotNull java.util.concurrent.CompletableFuture<Boolean> publish(@NotNull PrepareSession session) {
        return session.getTarget().saveMapAsync(World.Environment.NORMAL);
    }

    private static TNTRunConfig cfg(SetupTarget target) {
        return (TNTRunConfig) target.config();
    }
}

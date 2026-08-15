package ink.ziip.championshipscore.api.game.area.prepare.tntrun;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareFlowDefinition;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.SchematicStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StampStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunConfig;
import ink.ziip.championshipscore.api.game.config.GameSpawnResolver;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunLayout;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
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
        Location spawn = GameSpawnResolver.resolve(target.config());
        if (spawn != null) return spawn;
        World world = Bukkit.getWorld(target.worldName());
        return world == null ? CCConfig.LOBBY_LOCATION : cfg(target).getCopyGrid().origin(0).toLocation(world);
    }

    @Override
    public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        File schematic = new File(new File(new File(new File(target.plugin().getDataFolder(),
                "tntrun"), "schematics"), target.name()), "arena.schem");
        return List.of(
                new ConfirmWorldStep(player -> isInCorrectWorld(player, target), target.worldName()),
                new SchematicStep(plugin -> schematic, Component.text(GuiConfig.text("map-editor.games.tnt-run.setup.save-track-template-number-0")),
                        Component.text(GuiConfig.text("map-editor.games.tnt-run.setup.save-arena-schem-after-selecting-the-complete-single-track"))) {
                    @Override
                    public String capture(@NotNull PrepareSession session, @NotNull Player player) {
                        String result = super.capture(session, player);
                        try {
                            Vector[] selection = session.getPlugin().getWorldEditManager()
                                    .getPlayerSelection(player, true);
                            cfg(session.getTarget()).setAreaPos1(selection[0]);
                            cfg(session.getTarget()).setAreaPos2(selection[1]);
                        } catch (Exception ignored) {
                            // The parent capture already returns the useful WorldEdit selection error.
                        }
                        return result;
                    }
                },
                new WeSelectionStep("copy_zero_bounds", Component.text(GuiConfig.text("map-editor.games.tnt-run.setup.template-track-boundary-name")),
                        Component.text(GuiConfig.text("map-editor.games.tnt-run.setup.template-track-boundary-selection-hint")),
                        Material.BEDROCK,
                        t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                        (t, selection) -> {
                            cfg(t).setAreaPos1(selection[0]);
                            cfg(t).setAreaPos2(selection[1]);
                        }, Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.tnt-run.setup.the-complete-boundary-of-track-0-has-been-set"))),
                StampStep.adaptiveKeepingSource(plugin -> schematic,
                        (t, size) -> cfg(t).prepareCopyGrid(size),
                        (t, count) -> cfg(t).setCopies(count),
                        (session, world) -> {
                            TNTRunConfig previous = cfg(session.getTarget());
                            ArenaPreparer.clearAdditionalCopies(session.getPlugin(), world,
                                    previous.getCopyGrid(), previous.getCopies(), previous.getCopySize());
                        }),
                new StandAndRunStep("copy_spawn", Component.text(GuiConfig.text("map-editor.games.tnt-run.setup.template-track-spawn-point-name")),
                        Component.text(GuiConfig.text("map-editor.games.tnt-run.setup.stand-at-the-spawn-point-of-copy0-player-and-click")), Material.ELYTRA,
                        t -> cfg(t).getCopySpawn() != null, (t, loc) -> cfg(t).setCopySpawn(loc),
                        Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.tnt-run.setup.the-spawn-point-of-track-0-has-been-set"))),
                new StandAndRunStep("spectator_spawn", Component.text(GuiConfig.text("map-editor.copy.spectator-spawn-point")),
                        Component.text(GuiConfig.text("map-editor.copy.stand-in-a-spectator-position-and-click")), Material.ENDER_EYE,
                        t -> cfg(t).getSpectatorSpawnPoint() != null,
                        (t, loc) -> cfg(t).setSpectatorSpawnPoint(loc),
                        Utils.formatAdminSuccess(GuiConfig.text("map-editor.copy.spectator-spawn-point-has-been-set")))
        );
    }

    private static TNTRunConfig cfg(SetupTarget target) {
        return (TNTRunConfig) target.config();
    }
}

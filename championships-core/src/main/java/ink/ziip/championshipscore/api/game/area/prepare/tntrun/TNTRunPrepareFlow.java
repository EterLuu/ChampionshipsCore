package ink.ziip.championshipscore.api.game.area.prepare.tntrun;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareFlowDefinition;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.SchematicStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StampStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.config.GameSpawnResolver;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.api.game.tntrun.config.TNTRunConfig;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

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
        return world == null
                ? CCConfig.LOBBY_LOCATION
                : cfg(target).getCopyGrid().origin(0).toLocation(world);
    }

    @Override
    public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        File schematic =
                new File(
                        new File(
                                new File(
                                        new File(target.plugin().getDataFolder(), "tntrun"),
                                        "schematics"),
                                target.name()),
                        "arena.schem");
        return List.of(
                new ConfirmWorldStep(
                        player -> isInCorrectWorld(player, target), target.worldName()),
                new SchematicStep(
                        plugin -> schematic,
                        LegacyText.component(
                                GuiConfig.text(
                                        "map-editor.menus.step-list.games.tnt-run.items.save-template.title")),
                        LegacyText.component(
                                GuiConfig.line(
                                        "map-editor.menus.step-list.games.tnt-run.items.save-template.lore",
                                        0))) {
                    @Override
                    protected void onSchematicSaved(
                            @NotNull PrepareSession session,
                            @NotNull Player player,
                            @NotNull File file) {
                        Vector[] selection =
                                session.getPlugin()
                                        .getWorldEditManager()
                                        .getPlayerSelection(player, true);
                        cfg(session.getTarget()).setAreaPos1(selection[0]);
                        cfg(session.getTarget()).setAreaPos2(selection[1]);
                    }
                },
                new TNTRunEliminationHeightStep(),
                StampStep.adaptiveKeepingSource(
                        plugin -> schematic,
                        (t, size) -> cfg(t).prepareCopyGrid(size),
                        (t, count) -> cfg(t).setCopies(count),
                        (session, world) -> {
                            TNTRunConfig previous = cfg(session.getTarget());
                            ArenaPreparer.clearAdditionalCopies(
                                    session.getPlugin(),
                                    world,
                                    previous.getCopyGrid(),
                                    previous.getCopies(),
                                    previous.getCopySize());
                        }),
                new StandAndRunStep(
                        "copy_spawn",
                        LegacyText.component(
                                GuiConfig.text(
                                        "map-editor.menus.step-list.games.tnt-run.items.spawn.title")),
                        LegacyText.component(
                                GuiConfig.line(
                                        "map-editor.menus.step-list.games.tnt-run.items.spawn.lore",
                                        0)),
                        Material.ELYTRA,
                        t -> cfg(t).getCopySpawn() != null,
                        (t, loc) -> cfg(t).setCopySpawn(loc),
                        CoreMessages.formatAdminSuccess(MessageConfig.MAP_EDITOR_TNT_SPAWN_SET)),
                new StandAndRunStep(
                        "spectator_spawn",
                        LegacyText.component(
                                GuiConfig.text(
                                        "map-editor.menus.step-list.items.spectator-spawn.title")),
                        LegacyText.component(
                                GuiConfig.line(
                                        "map-editor.menus.step-list.items.spectator-spawn.lore",
                                        0)),
                        Material.ENDER_EYE,
                        t -> cfg(t).getSpectatorSpawnPoint() != null,
                        (t, loc) -> cfg(t).setSpectatorSpawnPoint(loc),
                        CoreMessages.formatAdminSuccess(
                                MessageConfig.MAP_EDITOR_STEP_SPECTATOR_SPAWN_POINT_SET)));
    }

    private static TNTRunConfig cfg(SetupTarget target) {
        return (TNTRunConfig) target.config();
    }
}

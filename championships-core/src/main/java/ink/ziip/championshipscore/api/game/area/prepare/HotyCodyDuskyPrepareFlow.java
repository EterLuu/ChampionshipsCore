package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.hotycodydusky.config.HotyCodyDuskyConfig;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
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

import java.util.List;

/** Guided setup for a Hoty Cody Dusky region in its shared permanent world. */
public final class HotyCodyDuskyPrepareFlow extends PrepareFlowDefinition {
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
        HotyCodyDuskyConfig config = cfg(target);
        if (config.getSpectatorSpawnPoint() != null) return config.getSpectatorSpawnPoint();
        World world = Bukkit.getWorld(target.worldName());
        return world == null ? CCConfig.LOBBY_LOCATION : world.getSpawnLocation();
    }

    @Override
    public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        java.io.File schematic =
                target.plugin()
                        .getFolder()
                        .resolve("hotycodydusky/schematics/" + target.name() + "/arena.schem")
                        .toFile();
        return List.of(
                new ConfirmWorldStep(
                        player -> isInCorrectWorld(player, target), target.worldName()),
                new ink.ziip.championshipscore.api.game.area.prepare.step.SchematicStep(
                        plugin -> schematic,
                        LegacyText.component(
                                GuiConfig.text(
                                        "map-editor.menus.step-list.games.hoty-cody-dusky.items.save-template.title")),
                        LegacyText.component(
                                GuiConfig.line(
                                        "map-editor.menus.step-list.games.hoty-cody-dusky.items.save-template.lore",
                                        0))) {
                    @Override
                    protected void onSchematicSaved(
                            PrepareSession session, Player player, java.io.File file) {
                        Vector[] selected =
                                session.getPlugin()
                                        .getWorldEditManager()
                                        .getPlayerSelection(player, true);
                        cfg(session.getTarget()).setAreaPos1(selected[0]);
                        cfg(session.getTarget()).setAreaPos2(selected[1]);
                    }
                },
                ink.ziip.championshipscore.api.game.area.prepare.step.StampStep
                        .adaptiveKeepingSource(
                                plugin -> schematic,
                                (t, size) -> cfg(t).prepareCopyGrid(size),
                                (t, count) -> cfg(t).setCopies(count),
                                (session, world) -> {
                                    var previous = cfg(session.getTarget());
                                    ink.ziip.championshipscore.api.game.arena.ArenaPreparer
                                            .clearAdditionalCopies(
                                                    session.getPlugin(),
                                                    world,
                                                    previous.getCopyGrid(),
                                                    previous.getCopies(),
                                                    previous.getCopySize());
                                }),
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
                        (t, l) -> cfg(t).setSpectatorSpawnPoint(l),
                        CoreMessages.formatAdminSuccess(
                                MessageConfig.MAP_EDITOR_STEP_SPECTATOR_SPAWN_POINT_SET)),
                new StandAndRunStep(
                        "player_spawn",
                        LegacyText.component(
                                GuiConfig.text(
                                        "map-editor.menus.step-list.games.hoty-cody-dusky.items.player-spawn.title")),
                        LegacyText.component(
                                GuiConfig.line(
                                        "map-editor.menus.step-list.games.hoty-cody-dusky.items.player-spawn.lore",
                                        0)),
                        Material.PLAYER_HEAD,
                        t -> cfg(t).getCopySpawn() != null,
                        (t, l) -> cfg(t).setCopySpawn(l),
                        CoreMessages.formatAdminSuccess(
                                MessageConfig.MAP_EDITOR_STEP_PLAYER_SPAWN_POINT_SET)));
    }

    private static HotyCodyDuskyConfig cfg(SetupTarget target) {
        return (HotyCodyDuskyConfig) target.config();
    }
}

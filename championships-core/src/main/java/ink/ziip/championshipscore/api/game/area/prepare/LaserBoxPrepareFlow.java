package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.SchematicStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StampStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.config.GameSpawnResolver;
import ink.ziip.championshipscore.api.game.laserbox.config.LaserBoxConfig;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Captures copy 0 and generates independent LaserBox arenas while preserving the hand-built source.
 */
public final class LaserBoxPrepareFlow extends PrepareFlowDefinition {
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

    private static LaserBoxConfig cfg(SetupTarget target) {
        return (LaserBoxConfig) target.config();
    }

    @Override
    public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        List<PrepareStep> steps = new ArrayList<>();
        File schematic =
                new File(
                        target.plugin().getDataFolder(),
                        "laserbox/schematics/" + target.name() + "/arena.schem");
        steps.add(
                new ConfirmWorldStep(
                        player -> isInCorrectWorld(player, target), target.worldName()));
        steps.add(
                new SchematicStep(
                        plugin -> schematic,
                        LegacyText.component(
                                GuiConfig.text(
                                        "map-editor.games.laserbox.menus.prepare.items.save-template.title")),
                        LegacyText.component(
                                GuiConfig.line(
                                        "map-editor.games.laserbox.menus.prepare.items.save-template.lore",
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
                });
        steps.add(
                StampStep.adaptiveKeepingSource(
                        plugin -> schematic,
                        (a, size) -> cfg(a).prepareCopyGrid(size),
                        (a, count) -> cfg(a).setCopyCount(count),
                        (session, world) -> {
                            LaserBoxConfig previous = cfg(session.getTarget());
                            ArenaPreparer.clearAdditionalCopies(
                                    session.getPlugin(),
                                    world,
                                    previous.getCopyGrid(),
                                    previous.getCopyCount(),
                                    previous.getCopySize());
                        }));
        steps.add(
                new WeSelectionStep(
                        "boundary",
                        LegacyText.component(
                                GuiConfig.text(
                                        "map-editor.games.laserbox.menus.prepare.items.boundary.title")),
                        LegacyText.component(
                                GuiConfig.line(
                                        "map-editor.games.laserbox.menus.prepare.items.boundary.lore",
                                        0)),
                        Material.BEDROCK,
                        a -> cfg(a).getAreaPos1() != null && cfg(a).getAreaPos2() != null,
                        (a, values) -> {
                            cfg(a).setAreaPos1(values[0]);
                            cfg(a).setAreaPos2(values[1]);
                        },
                        CoreMessages.formatAdminSuccess("LaserBox 对战边界已设置")));
        steps.add(
                new StandAndRunStep(
                        "right-spawn",
                        LegacyText.component(
                                GuiConfig.text(
                                        "map-editor.games.laserbox.menus.prepare.items.right-spawn.title")),
                        LegacyText.component(
                                GuiConfig.line(
                                        "map-editor.games.laserbox.menus.prepare.items.right-spawn.lore",
                                        0)),
                        Material.YELLOW_WOOL,
                        a -> cfg(a).getRightSpawnPoint() != null,
                        (a, location) -> cfg(a).setRightSpawnPoint(location),
                        "§a已设置黄队出生点"));
        steps.add(
                new StandAndRunStep(
                        "left-spawn",
                        LegacyText.component(
                                GuiConfig.text(
                                        "map-editor.games.laserbox.menus.prepare.items.left-spawn.title")),
                        LegacyText.component(
                                GuiConfig.line(
                                        "map-editor.games.laserbox.menus.prepare.items.left-spawn.lore",
                                        0)),
                        Material.PURPLE_WOOL,
                        a -> cfg(a).getLeftSpawnPoint() != null,
                        (a, location) -> cfg(a).setLeftSpawnPoint(location),
                        "§a已设置紫队出生点"));
        steps.add(
                new StandAndRunStep(
                        "spectator",
                        LegacyText.component(
                                GuiConfig.text(
                                        "map-editor.games.laserbox.menus.prepare.items.spectator.title")),
                        LegacyText.component(
                                GuiConfig.line(
                                        "map-editor.games.laserbox.menus.prepare.items.spectator.lore",
                                        0)),
                        Material.ENDER_EYE,
                        a -> cfg(a).getSpectatorSpawnPoint() != null,
                        (a, location) -> cfg(a).setSpectatorSpawnPoint(location),
                        "§a已设置观战点"));
        steps.add(
                new ListStep(
                        "supply-points",
                        LegacyText.component(
                                GuiConfig.text(
                                        "map-editor.games.laserbox.menus.prepare.items.supply-points.title")),
                        LegacyText.component(
                                GuiConfig.line(
                                        "map-editor.games.laserbox.menus.prepare.items.supply-points.lore",
                                        0)),
                        Material.CHEST,
                        a -> cfg(a).getSupplyPoints(),
                        (a, values) -> cfg(a).setSupplyPoints(values),
                        a -> cfg(a).getSupplyPoints() == null || cfg(a).getSupplyPoints().isEmpty(),
                        (a, value) -> {
                            List<String> values =
                                    cfg(a).getSupplyPoints() == null
                                            ? new ArrayList<>()
                                            : new ArrayList<>(cfg(a).getSupplyPoints());
                            values.add(value);
                            cfg(a).setSupplyPoints(values);
                        },
                        a -> cfg(a).setSupplyPoints(new ArrayList<>()),
                        a ->
                                cfg(a).getSupplyPoints() == null
                                        ? 0
                                        : cfg(a).getSupplyPoints().size()));
        return steps;
    }

    @Override
    public @NotNull List<String> validate(@NotNull PrepareSession session) {
        List<String> errors = new ArrayList<>(super.validate(session));
        try {
            cfg(session.getTarget()).validate();
        } catch (RuntimeException failure) {
            errors.add(failure.getMessage());
        }
        return errors;
    }
}

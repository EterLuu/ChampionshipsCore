package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.api.game.area.prepare.step.*;
import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.frostbite.config.FrostbiteConfig;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import org.bukkit.*;

import java.util.*;

/** Edits the shared coordinates of the four prebuilt, spatially separated terrain copies. */
public final class FrostbitePrepareFlow extends SnapshotMapPrepareFlow {
    public FrostbitePrepareFlow() {
        super(World.Environment.NORMAL);
    }

    private static FrostbiteConfig cfg(SetupTarget t) {
        return (FrostbiteConfig) t.config();
    }

    @Override
    public List<PrepareStep> buildSteps(SetupTarget target) {
        return List.of(
                new ConfirmWorldStep(p -> isInCorrectWorld(p, target), target.worldName()),
                new WeSelectionStep(
                        "arena",
                        LegacyText.component(
                                GuiConfig.text(
                                        "map-editor.games.frostbite.menus.prepare.items.boundary.title")),
                        LegacyText.component(
                                GuiConfig.line(
                                        "map-editor.games.frostbite.menus.prepare.items.boundary.lore",
                                        0)),
                        Material.BEDROCK,
                        t -> cfg(t).getArenaMin() != null && cfg(t).getArenaMax() != null,
                        (t, points) -> {
                            cfg(t).setArenaMin(points[0]);
                            cfg(t).setArenaMax(points[1]);
                        },
                        "§a已设置第一张地图边界"),
                new StandAndRunStep(
                        "spectator",
                        LegacyText.component(
                                GuiConfig.text(
                                        "map-editor.games.frostbite.menus.prepare.items.spectator.title")),
                        LegacyText.component(
                                GuiConfig.line(
                                        "map-editor.games.frostbite.menus.prepare.items.spectator.lore",
                                        0)),
                        Material.ENDER_EYE,
                        t -> cfg(t).getSpectatorSpawnPoint() != null,
                        (t, l) -> cfg(t).setSpectatorSpawnPoint(l),
                        "§a已设置观战出生点"),
                points(true),
                points(false));
    }

    private PrepareStep points(boolean spawn) {
        return new ListStep(
                spawn ? "spawns" : "items",
                LegacyText.component(
                        GuiConfig.text(
                                spawn
                                        ? "map-editor.games.frostbite.menus.prepare.items.spawns.title"
                                        : "map-editor.games.frostbite.menus.prepare.items.items.title")),
                LegacyText.component(
                        GuiConfig.line(
                                "map-editor.games.frostbite.menus.prepare.items.points.lore", 0)),
                spawn ? Material.ENDER_PEARL : Material.GOLD_BLOCK,
                t -> spawn ? cfg(t).getSpawnPoints() : cfg(t).getItemPoints(),
                (t, list) -> {
                    if (spawn) cfg(t).setSpawnPoints(list);
                    else cfg(t).setItemPoints(list);
                },
                t -> (spawn ? cfg(t).getSpawnPoints() : cfg(t).getItemPoints()).isEmpty(),
                (t, text) -> {
                    var list =
                            new ArrayList<>(
                                    spawn ? cfg(t).getSpawnPoints() : cfg(t).getItemPoints());
                    list.add(text);
                    if (spawn) cfg(t).setSpawnPoints(list);
                    else cfg(t).setItemPoints(list);
                },
                t -> {
                    if (spawn) cfg(t).setSpawnPoints(List.of());
                    else cfg(t).setItemPoints(List.of());
                },
                t -> (spawn ? cfg(t).getSpawnPoints() : cfg(t).getItemPoints()).size());
    }

    @Override
    public List<String> validate(PrepareSession session) {
        var errors = new ArrayList<>(super.validate(session));
        try {
            cfg(session.getTarget()).validate();
        } catch (IllegalArgumentException failure) {
            errors.add(failure.getMessage());
        }
        return errors;
    }
}

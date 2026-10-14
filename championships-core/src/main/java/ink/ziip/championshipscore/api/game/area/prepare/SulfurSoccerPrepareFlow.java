package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.api.game.sulfursoccer.config.SulfurSoccerConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import net.kyori.adventure.text.Component;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class SulfurSoccerPrepareFlow extends SnapshotMapPrepareFlow {
    public SulfurSoccerPrepareFlow() {
        super(World.Environment.NORMAL);
    }

    private static SulfurSoccerConfig cfg(SetupTarget target) {
        return (SulfurSoccerConfig) target.config();
    }

    @Override
    public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        List<PrepareStep> steps = new ArrayList<>();
        steps.add(
                new ConfirmWorldStep(
                        player -> isInCorrectWorld(player, target), target.worldName()));
        steps.add(
                selection(
                        "field",
                        MessageConfig.codeText("sulfur-soccer-prepare-flow.build-steps-1"),
                        Material.BEDROCK,
                        SulfurSoccerConfig::getAreaPos1,
                        SulfurSoccerConfig::getAreaPos2,
                        (c, v) -> {
                            c.setAreaPos1(v[0]);
                            c.setAreaPos2(v[1]);
                        }));
        steps.add(
                selection(
                        "right-half",
                        MessageConfig.codeText("sulfur-soccer-prepare-flow.build-steps-2"),
                        Material.RED_WOOL,
                        SulfurSoccerConfig::getRightAreaPos1,
                        SulfurSoccerConfig::getRightAreaPos2,
                        (c, v) -> {
                            c.setRightAreaPos1(v[0]);
                            c.setRightAreaPos2(v[1]);
                        }));
        steps.add(
                selection(
                        "left-half",
                        MessageConfig.codeText("sulfur-soccer-prepare-flow.build-steps-3"),
                        Material.BLUE_WOOL,
                        SulfurSoccerConfig::getLeftAreaPos1,
                        SulfurSoccerConfig::getLeftAreaPos2,
                        (c, v) -> {
                            c.setLeftAreaPos1(v[0]);
                            c.setLeftAreaPos2(v[1]);
                        }));
        steps.add(
                selection(
                        "right-goal",
                        MessageConfig.codeText("sulfur-soccer-prepare-flow.build-steps-4"),
                        Material.RED_STAINED_GLASS,
                        SulfurSoccerConfig::getRightGoalPos1,
                        SulfurSoccerConfig::getRightGoalPos2,
                        (c, v) -> {
                            c.setRightGoalPos1(v[0]);
                            c.setRightGoalPos2(v[1]);
                        }));
        steps.add(
                selection(
                        "left-goal",
                        MessageConfig.codeText("sulfur-soccer-prepare-flow.build-steps-5"),
                        Material.BLUE_STAINED_GLASS,
                        SulfurSoccerConfig::getLeftGoalPos1,
                        SulfurSoccerConfig::getLeftGoalPos2,
                        (c, v) -> {
                            c.setLeftGoalPos1(v[0]);
                            c.setLeftGoalPos2(v[1]);
                        }));
        steps.add(
                point(
                        "ball",
                        MessageConfig.codeText("sulfur-soccer-prepare-flow.build-steps-6"),
                        Material.SLIME_BALL,
                        SulfurSoccerConfig::getBallSpawnPoint,
                        SulfurSoccerConfig::setBallSpawnPoint));
        steps.add(
                point(
                        "spectator",
                        MessageConfig.codeText("sulfur-soccer-prepare-flow.build-steps-7"),
                        Material.ENDER_EYE,
                        SulfurSoccerConfig::getSpectatorSpawnPoint,
                        SulfurSoccerConfig::setSpectatorSpawnPoint));
        steps.add(
                spawns(
                        "right-spawns",
                        MessageConfig.codeText("sulfur-soccer-prepare-flow.build-steps-8"),
                        Material.RED_CONCRETE,
                        SulfurSoccerConfig::getRightSpawnPoints,
                        SulfurSoccerConfig::setRightSpawnPoints));
        steps.add(
                spawns(
                        "left-spawns",
                        MessageConfig.codeText("sulfur-soccer-prepare-flow.build-steps-9"),
                        Material.BLUE_CONCRETE,
                        SulfurSoccerConfig::getLeftSpawnPoints,
                        SulfurSoccerConfig::setLeftSpawnPoints));
        return steps;
    }

    private static WeSelectionStep selection(
            String key,
            String name,
            Material icon,
            Function<SulfurSoccerConfig, Vector> first,
            Function<SulfurSoccerConfig, Vector> second,
            BiConsumer<SulfurSoccerConfig, Vector[]> setter) {
        return new WeSelectionStep(
                key,
                LegacyText.component(name),
                Component.text(MessageConfig.codeText("sulfur-soccer-prepare-flow.selection-1")),
                icon,
                t -> first.apply(cfg(t)) != null && second.apply(cfg(t)) != null,
                (t, v) -> setter.accept(cfg(t), v),
                CoreMessages.formatAdminSuccess(
                        MessageConfig.codeText("sulfur-soccer-prepare-flow.selection-2", name)));
    }

    private static StandAndRunStep point(
            String key,
            String name,
            Material icon,
            Function<SulfurSoccerConfig, Location> getter,
            BiConsumer<SulfurSoccerConfig, Location> setter) {
        return new StandAndRunStep(
                key,
                LegacyText.component(name),
                Component.text(MessageConfig.codeText("sulfur-soccer-prepare-flow.point-1")),
                icon,
                t -> getter.apply(cfg(t)) != null,
                (t, l) -> setter.accept(cfg(t), l),
                CoreMessages.formatAdminSuccess(
                        MessageConfig.codeText("sulfur-soccer-prepare-flow.selection-2", name)));
    }

    private static ListStep spawns(
            String key,
            String name,
            Material icon,
            Function<SulfurSoccerConfig, List<String>> getter,
            BiConsumer<SulfurSoccerConfig, List<String>> setter) {
        return new ListStep(
                key,
                LegacyText.component(name),
                Component.text(MessageConfig.codeText("sulfur-soccer-prepare-flow.spawns-1")),
                icon,
                t -> getter.apply(cfg(t)),
                (t, list) -> setter.accept(cfg(t), list),
                t -> getter.apply(cfg(t)) == null || getter.apply(cfg(t)).size() != 4,
                (t, value) -> {
                    List<String> list =
                            new ArrayList<>(
                                    getter.apply(cfg(t)) == null
                                            ? List.of()
                                            : getter.apply(cfg(t)));
                    list.add(value);
                    setter.accept(cfg(t), list);
                },
                t -> setter.accept(cfg(t), new ArrayList<>()),
                t -> getter.apply(cfg(t)) == null ? 0 : getter.apply(cfg(t)).size());
    }

    @Override
    public @NotNull List<String> validate(@NotNull PrepareSession session) {
        List<String> errors = new ArrayList<>(super.validate(session));
        try {
            cfg(session.getTarget()).validate();
        } catch (IllegalArgumentException failure) {
            errors.add(failure.getMessage());
        }
        return errors;
    }
}

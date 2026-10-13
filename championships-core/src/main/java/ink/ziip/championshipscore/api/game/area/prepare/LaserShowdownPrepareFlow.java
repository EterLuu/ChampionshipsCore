package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.FourArenaSchematicStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.FourArenaStampStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.lasershowdown.config.LaserShowdownConfig;
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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Guided setup for one Laser Showdown region in the shared permanent world. */
public final class LaserShowdownPrepareFlow extends PrepareFlowDefinition {

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
        Location spectator = cfg(target).getSpectatorSpawnPoint();
        if (spectator != null) return spectator;
        World world = Bukkit.getWorld(target.worldName());
        return world == null ? CCConfig.LOBBY_LOCATION : world.getSpawnLocation();
    }

    @Override
    public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        List<PrepareStep> steps = new ArrayList<>();
        steps.add(
                new ConfirmWorldStep(
                        player -> isInCorrectWorld(player, target), target.worldName()));
        steps.add(new FourArenaSchematicStep(target));
        steps.add(new FourArenaStampStep());
        steps.add(
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
                                MessageConfig.MAP_EDITOR_STEP_SPECTATOR_SPAWN_POINT_SET)));
        String lane = "area1";
        steps.add(
                new ListStep(
                        "spawns",
                        LegacyText.component(
                                GuiConfig.text(
                                        "map-editor.games.lasershowdown.menus.prepare.items.spawns.title")),
                        LegacyText.component(
                                GuiConfig.line(
                                        "map-editor.games.lasershowdown.menus.prepare.items.spawns.lore",
                                        0)),
                        Material.PLAYER_HEAD,
                        t -> laneValues(t, lane),
                        (t, values) -> section(t).set(lane, values),
                        t -> laneValues(t, lane).isEmpty(),
                        (t, value) -> {
                            List<String> values = laneValues(t, lane);
                            values.add(value);
                            section(t).set(lane, values);
                        },
                        t -> section(t).set(lane, new ArrayList<>()),
                        t -> laneValues(t, lane).size()));
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

    private static LaserShowdownConfig cfg(SetupTarget target) {
        return (LaserShowdownConfig) target.config();
    }

    private static ConfigurationSection section(SetupTarget target) {
        return cfg(target).ensurePlayerSpawnPoints();
    }

    private static List<String> laneValues(SetupTarget target, String lane) {
        return new ArrayList<>(section(target).getStringList(lane));
    }
}

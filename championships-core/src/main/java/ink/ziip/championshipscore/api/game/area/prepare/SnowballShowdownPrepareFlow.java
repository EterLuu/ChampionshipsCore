package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownConfig;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Guided setup for one Snowball Showdown region in the shared permanent world. */
public final class SnowballShowdownPrepareFlow extends PrepareFlowDefinition {
    private static final List<String> LANES = List.of("area1", "area2", "area3", "area4");
    @Override public @NotNull String worldName(@NotNull SetupTarget target) { return target.worldName(); }
    @Override public boolean isInCorrectWorld(@NotNull Player player, @NotNull SetupTarget target) {
        return target.worldName().equals(player.getWorld().getName());
    }
    @Override public @NotNull Location copyZeroLocation(@NotNull SetupTarget target) {
        Location spectator = cfg(target).getSpectatorSpawnPoint();
        if (spectator != null) return spectator;
        World world = Bukkit.getWorld(target.worldName());
        return world == null ? CCConfig.LOBBY_LOCATION : world.getSpawnLocation();
    }
    @Override public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        List<PrepareStep> steps = new ArrayList<>();
        steps.add(new ConfirmWorldStep(player -> isInCorrectWorld(player, target), target.worldName()));
        steps.add(new WeSelectionStep("area_pos", Component.text(GuiConfig.text("area-prepare-snowballshowdownprepareflow.text-001")), Component.text(GuiConfig.text("area-prepare-snowballshowdownprepareflow.text-002")),
                Material.BEDROCK, t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                (t, v) -> { cfg(t).setAreaPos1(v[0]); cfg(t).setAreaPos2(v[1]); }, Utils.formatAdminSuccess(GuiConfig.text("area-prepare-snowballshowdownprepareflow.text-003"))));
        steps.add(new StandAndRunStep("spectator_spawn", Component.text(GuiConfig.text("area-prepare-snowballshowdownprepareflow.text-004")), Component.text(GuiConfig.text("area-prepare-snowballshowdownprepareflow.text-005")),
                Material.ENDER_EYE, t -> cfg(t).getSpectatorSpawnPoint() != null,
                (t, l) -> cfg(t).setSpectatorSpawnPoint(l), Utils.formatAdminSuccess(GuiConfig.text("area-prepare-snowballshowdownprepareflow.text-006"))));
        for (String lane : LANES) {
            steps.add(new ListStep("player_spawn_" + lane, Component.text(GuiConfig.text("area-prepare-snowballshowdownprepareflow.text-007") + lane),
                    Component.text(GuiConfig.text("area-prepare-snowballshowdownprepareflow.text-008")), Material.PLAYER_HEAD,
                    t -> laneValues(t, lane), (t, values) -> section(t).set(lane, values),
                    t -> laneValues(t, lane).isEmpty(),
                    (t, value) -> { List<String> values = laneValues(t, lane); values.add(value); section(t).set(lane, values); },
                    t -> section(t).set(lane, new ArrayList<>()), t -> laneValues(t, lane).size()));
        }
        return steps;
    }
    private static SnowballShowdownConfig cfg(SetupTarget target) { return (SnowballShowdownConfig) target.config(); }
    private static ConfigurationSection section(SetupTarget target) { return cfg(target).ensurePlayerSpawnPoints(); }
    private static List<String> laneValues(SetupTarget target, String lane) {
        return new ArrayList<>(section(target).getStringList(lane));
    }
}

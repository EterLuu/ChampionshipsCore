package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.skywars.SkyWarsConfig;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Guided setup for one directly edited SkyWars map. */
public final class SkyWarsPrepareFlow extends SnapshotMapPrepareFlow {
    public SkyWarsPrepareFlow() {
        super(World.Environment.NORMAL);
    }

    @Override public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        List<PrepareStep> steps = new ArrayList<>();
        steps.add(new ConfirmWorldStep(player -> isInCorrectWorld(player, target), target.worldName()));
        steps.add(new WeSelectionStep("area_pos", Component.text(GuiConfig.text("map-editor.games.sky-wars.setup.map-borders")),
                Component.text(GuiConfig.text("map-editor.games.sky-wars.setup.use-worldedit-to-select-the-complete-boundary-of-this-empty-island")), Material.BEDROCK,
                t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                (t, v) -> { cfg(t).setAreaPos1(v[0]); cfg(t).setAreaPos2(v[1]); },
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.sky-wars.setup.map-boundaries-set"))));
        steps.add(new StandAndRunStep("boundary_center", Component.text(GuiConfig.text("map-editor.games.sky-wars.setup.border-center-point")),
                Component.text(GuiConfig.text("map-editor.games.sky-wars.setup.stand-at-the-center-of-the-horizontal-border-and-the-shrink-effect-and-click")), Material.COMPASS,
                t -> cfg(t).getBoundaryCenterPoint() != null, (t, l) -> cfg(t).setBoundaryCenterPoint(l),
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.sky-wars.setup.boundary-center-point-set"))));
        steps.add(new StandAndRunStep("spectator_spawn", Component.text(GuiConfig.text("map-editor.copy.spectator-spawn-point")),
                Component.text(GuiConfig.text("map-editor.copy.stand-in-a-spectator-position-and-click")), Material.ENDER_EYE,
                t -> cfg(t).getSpectatorSpawnPoint() != null, (t, l) -> cfg(t).setSpectatorSpawnPoint(l),
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.copy.spectator-spawn-point-has-been-set"))));
        steps.add(new ListStep("team_spawn_points", Component.text(GuiConfig.text("map-editor.games.sky-wars.setup.team-spawn-point")),
                Component.text(GuiConfig.text("map-editor.games.sky-wars.setup.add-spawn-positions-for-each-team-one-by-one")), Material.LIME_WOOL,
                t -> list(t), (t, values) -> cfg(t).setTeamSpawnPoints(values),
                t -> list(t).isEmpty(), (t, s) -> { List<String> l = list(t); l.add(s); cfg(t).setTeamSpawnPoints(l); },
                t -> cfg(t).setTeamSpawnPoints(new ArrayList<>()), t -> list(t).size()));
        return steps;
    }
    private static SkyWarsConfig cfg(SetupTarget target) { return (SkyWarsConfig) target.config(); }
    private static List<String> list(SetupTarget target) {
        List<String> list = cfg(target).getTeamSpawnPoints();
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }
}

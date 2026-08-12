package ink.ziip.championshipscore.api.game.area.prepare.buildmart;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareFlowDefinition;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.SelectedBlockStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Build Mart flow: a hand-built resource hub plus a replicated, editable base template. */
public class BuildMartPrepareFlow extends PrepareFlowDefinition {
    @Override public @NotNull String worldName(@NotNull SetupTarget target) { return target.worldName(); }

    @Override public boolean isInCorrectWorld(@NotNull Player player, @NotNull SetupTarget target) {
        return target.worldName().equals(player.getWorld().getName());
    }

    @Override public @NotNull Location copyZeroLocation(@NotNull SetupTarget target) {
        World world = Bukkit.getWorld(target.worldName());
        Location spectator = cfg(target).getSpectatorSpawnPoint();
        if (spectator != null) {
            spectator = spectator.clone();
            if (spectator.getWorld() == null) spectator.setWorld(world);
            if (spectator.getWorld() != null) return spectator;
        }
        return world == null ? CCConfig.LOBBY_LOCATION : cfg(target).getBaseGrid().origin(0).toLocation(world);
    }

    @Override public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        File dir = new File(new File(new File(target.plugin().getDataFolder(), "buildmart"),
                "schematics"), target.name());
        File base = new File(dir, "base.schem");
        List<PrepareStep> steps = new ArrayList<>();
        steps.add(new ConfirmWorldStep(player -> isInCorrectWorld(player, target), target.worldName()));

        steps.add(selection("hub", GuiConfig.text("prepare-buildmart-buildmartprepareflow.text-001"), Material.CHEST,
                t -> cfg(t).getHubPos1() != null && cfg(t).getHubPos2() != null,
                (t, v) -> { cfg(t).setHubPos1(v[0]); cfg(t).setHubPos2(v[1]); }));
        steps.add(new BuildMartBaseSchematicStep(base));
        steps.add(new BuildMartStampStep(base));

        steps.add(new BuildMartWindZoneListStep());
        steps.add(new BuildMartMaterialZoneStep());
        steps.add(point("spectator_spawn", GuiConfig.text("prepare-buildmart-buildmartprepareflow.text-002"), Material.ENDER_EYE,
                t -> cfg(t).getSpectatorSpawnPoint() != null, (t, l) -> cfg(t).setSpectatorSpawnPoint(l)));
        steps.add(new StandAndRunStep("hub_portal", Component.text(GuiConfig.text("prepare-buildmart-buildmartprepareflow.text-003")),
                Component.text(GuiConfig.text("prepare-buildmart-buildmartprepareflow.text-004")), Material.OBSIDIAN,
                t -> cfg(t).getHubPortalPoint() != null,
                (t, l) -> cfg(t).setHubPortalPoint(l), Utils.formatAdminSuccess(GuiConfig.text("prepare-buildmart-buildmartprepareflow.text-005"))));
        steps.add(new BuildMartFloorSelectionStep("golden_display", Component.text(GuiConfig.text("prepare-buildmart-buildmartprepareflow.text-006")),
                Material.GOLD_BLOCK, t -> cfg(t).getGoldenDisplayPoint() != null,
                (t, l) -> cfg(t).setGoldenDisplayPoint(l)));

        String[] points = {"portal", "normal-plot-1",
                "normal-plot-2", "normal-plot-3", "normal-ref-1", "normal-ref-2",
                "normal-ref-3", "golden-plot"};
        for (String key : points)
            steps.add(basePoint(key, false));
        String[] submits = {"normal-submit-1", "normal-submit-2", "normal-submit-3", "golden-submit"};
        for (String key : submits)
            steps.add(basePoint(key, true));
        return steps;
    }

    @Override public @NotNull CompletableFuture<Boolean> publish(@NotNull PrepareSession session) {
        return session.getTarget().saveMap(World.Environment.NORMAL);
    }

    private static PrepareStep selection(String key, String name, Material icon,
            java.util.function.Predicate<SetupTarget> predicate,
            java.util.function.BiConsumer<SetupTarget, org.bukkit.util.Vector[]> setter) {
        return new WeSelectionStep(key, Component.text(name), Component.text(GuiConfig.text("prepare-buildmart-buildmartprepareflow.text-007")),
                icon, predicate, setter, Utils.formatAdminSuccess(GuiConfig.text("prepare-buildmart-buildmartprepareflow.text-008") + name + "。"));
    }

    private static PrepareStep point(String key, String name, Material icon,
            java.util.function.Predicate<SetupTarget> predicate,
            java.util.function.BiConsumer<SetupTarget, Location> setter) {
        return new StandAndRunStep(key, Component.text(name), Component.text(GuiConfig.text("prepare-buildmart-buildmartprepareflow.text-009")),
                icon, predicate, setter, Utils.formatAdminSuccess(GuiConfig.text("prepare-buildmart-buildmartprepareflow.text-008") + name + "。"));
    }

    private static PrepareStep basePoint(String key, boolean selectedBlock) {
        String display = GuiConfig.text("prepare-buildmart-buildmartprepareflow.text-010") + key;
        if (selectedBlock) {
            return new SelectedBlockStep("base_" + key, Component.text(display),
                    Component.text(GuiConfig.text("prepare-buildmart-buildmartprepareflow.text-011")), Material.STONE_BUTTON,
                    t -> cfg(t).hasBaseLocation(key), (t, l) -> cfg(t).setBaseLocation(key, l));
        }
        if (!key.equals("portal")) {
            return new BuildMartFloorSelectionStep("base_" + key, Component.text(display), Material.BRICKS,
                    t -> cfg(t).hasBaseLocation(key), (t, l) -> cfg(t).setBaseLocation(key, l));
        }
        Component description = Component.text(GuiConfig.text("prepare-buildmart-buildmartprepareflow.text-012"));
        return new StandAndRunStep("base_" + key, Component.text(display), description, Material.BRICKS,
                t -> cfg(t).hasBaseLocation(key), (t, l) -> cfg(t).setBaseLocation(key, l),
                Utils.formatAdminSuccess(GuiConfig.text("prepare-buildmart-buildmartprepareflow.text-008") + display + "。"));
    }

    private static BuildMartConfig cfg(SetupTarget target) { return (BuildMartConfig) target.config(); }
}

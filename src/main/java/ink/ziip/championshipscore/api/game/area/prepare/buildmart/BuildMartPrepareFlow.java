package ink.ziip.championshipscore.api.game.area.prepare.buildmart;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareFlowDefinition;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.SchematicStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.SelectedBlockStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartLayout;
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

/** Unified Build Mart flow: hub/base assets, physical build, global geometry and copy-0 base template. */
public class BuildMartPrepareFlow extends PrepareFlowDefinition {
    @Override public @NotNull String worldName(@NotNull SetupTarget target) { return target.worldName(); }

    @Override public boolean isInCorrectWorld(@NotNull Player player, @NotNull SetupTarget target) {
        return target.worldName().equals(player.getWorld().getName());
    }

    @Override public @NotNull Location copyZeroLocation(@NotNull SetupTarget target) {
        World world = Bukkit.getWorld(target.worldName());
        return world == null ? CCConfig.LOBBY_LOCATION : cfg(target).getBaseGrid().origin(0).toLocation(world);
    }

    @Override public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        File dir = new File(new File(new File(target.plugin().getDataFolder(), "buildmart"),
                "schematics"), target.name());
        File hub = new File(dir, "hub.schem");
        File base = new File(dir, "base.schem");
        List<PrepareStep> steps = new ArrayList<>();
        steps.add(new ConfirmWorldStep(player -> isInCorrectWorld(player, target), target.worldName()));
        steps.add(new SchematicStep("hub_schematic", plugin -> hub, Component.text("保存大厅模板"),
                Component.text("选中完整资源大厅后保存 hub.schem")));
        steps.add(new SchematicStep("base_schematic", plugin -> base, Component.text("保存基地模板"),
                Component.text("选中完整单队基地后保存 base.schem")));
        steps.add(new BuildMartStampStep(hub, base));

        steps.add(selection("area", "整张地图边界", Material.BEDROCK,
                t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                (t, v) -> { cfg(t).setAreaPos1(v[0]); cfg(t).setAreaPos2(v[1]); }));
        steps.add(selection("hub", "资源大厅边界", Material.CHEST,
                t -> cfg(t).getHubPos1() != null && cfg(t).getHubPos2() != null,
                (t, v) -> { cfg(t).setHubPos1(v[0]); cfg(t).setHubPos2(v[1]); }));
        steps.add(selection("hub_return", "大厅返回基地触发区", Material.ENDER_PEARL,
                t -> cfg(t).getHubReturnPos1() != null && cfg(t).getHubReturnPos2() != null,
                (t, v) -> { cfg(t).setHubReturnPos1(v[0]); cfg(t).setHubReturnPos2(v[1]); }));
        steps.add(point("spectator_spawn", "旁观者出生点", Material.ENDER_EYE,
                t -> cfg(t).getSpectatorSpawnPoint() != null, (t, l) -> cfg(t).setSpectatorSpawnPoint(l)));
        steps.add(point("hub_spawn", "大厅出生点", Material.COMPASS,
                t -> cfg(t).getHubSpawnPoint() != null, (t, l) -> cfg(t).setHubSpawnPoint(l)));
        steps.add(point("golden_display", "黄金蓝图展示点", Material.GOLD_BLOCK,
                t -> cfg(t).getGoldenDisplayPoint() != null, (t, l) -> cfg(t).setGoldenDisplayPoint(l)));

        String[] points = {"spawn", "portal-pos1", "portal-pos2", "normal-plot-1",
                "normal-plot-2", "normal-plot-3", "normal-ref-1", "normal-ref-2",
                "normal-ref-3", "golden-plot", "golden-ref"};
        for (String key : points)
            steps.add(basePoint(key, false));
        String[] submits = {"normal-submit-1", "normal-submit-2", "normal-submit-3", "golden-submit"};
        for (String key : submits)
            steps.add(basePoint(key, true));
        return steps;
    }

    @Override public @NotNull java.util.concurrent.CompletableFuture<Boolean> publish(@NotNull PrepareSession session) {
        return session.getTarget().saveMapAsync(World.Environment.NORMAL);
    }

    private static PrepareStep selection(String key, String name, Material icon,
            java.util.function.Predicate<SetupTarget> predicate,
            java.util.function.BiConsumer<SetupTarget, org.bukkit.util.Vector[]> setter) {
        return new WeSelectionStep(key, Component.text(name), Component.text("用 WorldEdit 选区后点击"),
                icon, predicate, setter, Utils.formatAdminSuccess("已设置" + name + "。"));
    }

    private static PrepareStep point(String key, String name, Material icon,
            java.util.function.Predicate<SetupTarget> predicate,
            java.util.function.BiConsumer<SetupTarget, Location> setter) {
        return new StandAndRunStep(key, Component.text(name), Component.text("站到目标位置后点击"),
                icon, predicate, setter, Utils.formatAdminSuccess("已设置" + name + "。"));
    }

    private static PrepareStep basePoint(String key, boolean selectedBlock) {
        String display = "基地模板 · " + key;
        if (selectedBlock) {
            return new SelectedBlockStep("base_" + key, Component.text(display),
                    Component.text("用 WorldEdit 精确选中提交按钮方块"), Material.STONE_BUTTON,
                    t -> cfg(t).hasBaseLocation(key), (t, l) -> cfg(t).setBaseLocation(key, l));
        }
        return point("base_" + key, display, Material.BRICKS,
                t -> cfg(t).hasBaseLocation(key), (t, l) -> cfg(t).setBaseLocation(key, l));
    }

    private static BuildMartConfig cfg(SetupTarget target) { return (BuildMartConfig) target.config(); }
}

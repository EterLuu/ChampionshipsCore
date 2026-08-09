package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ItemListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalConfig;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Guided setup for one directly edited Dragon Egg Carnival map. */
public final class DragonEggCarnivalPrepareFlow extends SnapshotMapPrepareFlow {
    public DragonEggCarnivalPrepareFlow() {
        super(World.Environment.THE_END);
    }

    @Override public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        List<PrepareStep> steps = new ArrayList<>();
        steps.add(new ConfirmWorldStep(player -> isInCorrectWorld(player, target), target.worldName()));
        steps.add(new WeSelectionStep("area_pos", Component.text("场地边界"), Component.text("用 WorldEdit 选取完整比赛区域"),
                Material.BEDROCK, t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                (t, v) -> { cfg(t).setAreaPos1(v[0]); cfg(t).setAreaPos2(v[1]); }, Utils.formatAdminSuccess("已设置场地边界。")));
        steps.add(location("spectator_spawn", "旁观者出生点", Material.ENDER_EYE,
                t -> cfg(t).getSpectatorSpawnPoint() != null, (t, l) -> cfg(t).setSpectatorSpawnPoint(l), "已设置旁观者出生点。"));
        steps.add(location("dragon_spawn", "末影龙出生点", Material.DRAGON_EGG,
                t -> cfg(t).getDragonSpawnPoint() != null, (t, l) -> cfg(t).setDragonSpawnPoint(l), "已设置末影龙出生点。"));
        steps.add(location("dragon_egg_spawn", "龙蛋出生点", Material.DRAGON_EGG,
                t -> cfg(t).getDragonEggSpawnPoint() != null, (t, l) -> cfg(t).setDragonEggSpawnPoint(l), "已设置龙蛋出生点。"));
        steps.add(list("right_spawn_points", "右侧队伍出生点", "逐个添加右侧队伍出生位置", Material.GREEN_WOOL,
                t -> cfg(t).getRightSpawnPoints(), (t, l) -> cfg(t).setRightSpawnPoints(l)));
        steps.add(list("left_spawn_points", "左侧队伍出生点", "逐个添加左侧队伍出生位置", Material.RED_WOOL,
                t -> cfg(t).getLeftSpawnPoints(), (t, l) -> cfg(t).setLeftSpawnPoints(l)));
        steps.add(new ItemListStep());
        return steps;
    }
    private static DragonEggCarnivalConfig cfg(SetupTarget target) { return (DragonEggCarnivalConfig) target.config(); }
    private static PrepareStep location(String key, String name, Material icon, java.util.function.Predicate<SetupTarget> set,
                                        java.util.function.BiConsumer<SetupTarget, Location> setter, String done) {
        return new StandAndRunStep(key, Component.text(name), Component.text("站到目标位置后点击"), icon, set, setter,
                Utils.formatAdminSuccess(done));
    }
    private static PrepareStep list(String key, String name, String desc, Material icon,
                                    java.util.function.Function<SetupTarget, List<String>> getter,
                                    java.util.function.BiConsumer<SetupTarget, List<String>> setter) {
        return new ListStep(key, Component.text(name), Component.text(desc), icon,
                t -> values(getter.apply(t)), setter,
                t -> values(getter.apply(t)).isEmpty(),
                (t, value) -> { List<String> l = values(getter.apply(t)); l.add(value); setter.accept(t, l); },
                t -> setter.accept(t, new ArrayList<>()), t -> values(getter.apply(t)).size());
    }
    private static List<String> values(List<String> values) { return values == null ? new ArrayList<>() : new ArrayList<>(values); }
}

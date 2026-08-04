package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltConfig;
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

/** Guided setup for one directly edited Dodgebolt final arena. */
public final class DodgeboltPrepareFlow extends SnapshotMapPrepareFlow {
    public DodgeboltPrepareFlow() {
        super(World.Environment.NORMAL);
    }

    @Override public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        List<PrepareStep> steps = new ArrayList<>();
        steps.add(new ConfirmWorldStep(player -> isInCorrectWorld(player, target), target.worldName()));
        steps.add(selection("area", "比赛区边界", Material.BEDROCK,
                t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                (t, v) -> { cfg(t).setAreaPos1(v[0]); cfg(t).setAreaPos2(v[1]); }));
        steps.add(selection("spectator_area", "观赛活动区边界", Material.SPYGLASS,
                t -> cfg(t).getSpectatorAreaPos1() != null && cfg(t).getSpectatorAreaPos2() != null,
                (t, v) -> { cfg(t).setSpectatorAreaPos1(v[0]); cfg(t).setSpectatorAreaPos2(v[1]); }));
        steps.add(selection("platform", "可收缩平台", Material.SMOOTH_STONE,
                t -> cfg(t).getPlatformPos1() != null && cfg(t).getPlatformPos2() != null,
                (t, v) -> { cfg(t).setPlatformPos1(v[0]); cfg(t).setPlatformPos2(v[1]); }));
        steps.add(selection("right_area", "右队活动区域", Material.RED_STAINED_GLASS,
                t -> cfg(t).getRightAreaPos1() != null && cfg(t).getRightAreaPos2() != null,
                (t, v) -> { cfg(t).setRightAreaPos1(v[0]); cfg(t).setRightAreaPos2(v[1]); }));
        steps.add(selection("left_area", "左队活动区域", Material.BLUE_STAINED_GLASS,
                t -> cfg(t).getLeftAreaPos1() != null && cfg(t).getLeftAreaPos2() != null,
                (t, v) -> { cfg(t).setLeftAreaPos1(v[0]); cfg(t).setLeftAreaPos2(v[1]); }));
        steps.add(selection("right_shoot", "右队射箭区域", Material.RED_CONCRETE,
                t -> cfg(t).getRightShootPos1() != null && cfg(t).getRightShootPos2() != null,
                (t, v) -> { cfg(t).setRightShootPos1(v[0]); cfg(t).setRightShootPos2(v[1]); }));
        steps.add(selection("left_shoot", "左队射箭区域", Material.BLUE_CONCRETE,
                t -> cfg(t).getLeftShootPos1() != null && cfg(t).getLeftShootPos2() != null,
                (t, v) -> { cfg(t).setLeftShootPos1(v[0]); cfg(t).setLeftShootPos2(v[1]); }));
        steps.add(location("spectator_spawn", "旁观出生点", Material.ENDER_EYE,
                t -> cfg(t).getSpectatorSpawnPoint() != null,
                (t, l) -> cfg(t).setSpectatorSpawnPoint(l)));
        steps.add(location("introduction_spawn", "规则介绍出生点", Material.BOOK,
                t -> cfg(t).getIntroductionSpawnPoint() != null,
                (t, l) -> cfg(t).setIntroductionSpawnPoint(l)));
        steps.add(list("right_spawns", "右队选手出生点", Material.RED_WOOL,
                t -> cfg(t).getRightSpawnPoints(), (t, l) -> cfg(t).setRightSpawnPoints(l)));
        steps.add(list("left_spawns", "左队选手出生点", Material.BLUE_WOOL,
                t -> cfg(t).getLeftSpawnPoints(), (t, l) -> cfg(t).setLeftSpawnPoints(l)));
        steps.add(location("right_arrow", "右队箭刷新点", Material.ARROW,
                t -> hasPoint(cfg(t).getRightArrowSpawnPoint()),
                (t, l) -> cfg(t).setRightArrowSpawnPoint(Utils.getLocationConfigString(l))));
        steps.add(location("left_arrow", "左队箭刷新点", Material.SPECTRAL_ARROW,
                t -> hasPoint(cfg(t).getLeftArrowSpawnPoint()),
                (t, l) -> cfg(t).setLeftArrowSpawnPoint(Utils.getLocationConfigString(l))));
        return steps;
    }

    @Override public @NotNull List<String> validate(@NotNull PrepareSession session) {
        List<String> errors = new ArrayList<>(super.validate(session));
        DodgeboltConfig config = cfg(session.getTarget());
        requireCount(errors, "右队选手出生点", config.getRightSpawnPoints(), 4);
        requireCount(errors, "左队选手出生点", config.getLeftSpawnPoints(), 4);
        requirePoint(errors, "右队箭刷新点", config.getRightArrowSpawnPoint());
        requirePoint(errors, "左队箭刷新点", config.getLeftArrowSpawnPoint());
        if (!isInSpectatorArea(config, config.getSpectatorSpawnPoint()))
            errors.add("旁观出生点必须位于观赛活动区内");
        if (isInArea(config, config.getSpectatorSpawnPoint()))
            errors.add("旁观出生点必须位于比赛区外");
        return errors;
    }

    private static WeSelectionStep selection(String key, String name, Material icon,
                                              java.util.function.Predicate<SetupTarget> set,
                                              java.util.function.BiConsumer<SetupTarget, Vector[]> setter) {
        return new WeSelectionStep(key, Component.text(name), Component.text("用 WorldEdit 选取两个端点"),
                icon, set, setter, Utils.formatAdminSuccess("已设置" + name + "。"));
    }

    private static StandAndRunStep location(String key, String name, Material icon,
                                            java.util.function.Predicate<SetupTarget> set,
                                            java.util.function.BiConsumer<SetupTarget, Location> setter) {
        return new StandAndRunStep(key, Component.text(name), Component.text("站到目标位置后点击"),
                icon, set, setter, Utils.formatAdminSuccess("已设置" + name + "。"));
    }

    private static ListStep list(String key, String name, Material icon,
                                 java.util.function.Function<SetupTarget, List<String>> getter,
                                 java.util.function.BiConsumer<SetupTarget, List<String>> setter) {
        return new ListStep(key, Component.text(name), Component.text("逐个添加点位"), icon,
                t -> values(getter.apply(t)), setter,
                t -> values(getter.apply(t)).isEmpty(),
                (t, value) -> { List<String> list = values(getter.apply(t)); list.add(value); setter.accept(t, list); },
                t -> setter.accept(t, new ArrayList<>()), t -> values(getter.apply(t)).size());
    }

    private static void requireCount(List<String> errors, String name, List<String> values, int minimum) {
        int count = values == null ? 0 : values.size();
        if (count < minimum) errors.add(name + "（至少 " + minimum + " 个，当前 " + count + " 个）");
    }

    private static void requirePoint(List<String> errors, String name, String value) {
        if (!hasPoint(value)) errors.add(name + "未设置");
    }

    private static boolean isInSpectatorArea(DodgeboltConfig config, Location location) {
        return isInBox(location, config.getSpectatorAreaPos1(), config.getSpectatorAreaPos2());
    }

    private static boolean isInArea(DodgeboltConfig config, Location location) {
        return isInBox(location, config.getAreaPos1(), config.getAreaPos2());
    }

    private static boolean isInBox(Location location, Vector pos1, Vector pos2) {
        return location != null && pos1 != null && pos2 != null && location.toVector().isInAABB(pos1, pos2);
    }

    private static boolean hasPoint(String value) { return value != null && !value.isBlank(); }

    private static List<String> values(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static DodgeboltConfig cfg(SetupTarget target) { return (DodgeboltConfig) target.config(); }
}

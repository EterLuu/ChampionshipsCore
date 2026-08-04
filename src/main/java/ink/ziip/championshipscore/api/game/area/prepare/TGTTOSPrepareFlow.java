package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSConfig;
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

import java.util.ArrayList;
import java.util.List;

/** Guided setup for a region in TGTTOS's shared permanent world. */
public final class TGTTOSPrepareFlow extends PrepareFlowDefinition {
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
        steps.add(new WeSelectionStep("area_pos", Component.text("赛道边界"), Component.text("用 WorldEdit 选取此赛道的完整区域"),
                Material.BEDROCK, t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                (t, v) -> { cfg(t).setAreaPos1(v[0]); cfg(t).setAreaPos2(v[1]); },
                Utils.formatAdminSuccess("已设置赛道边界。")));
        steps.add(location("spectator_spawn", "旁观者出生点", Material.ENDER_EYE,
                t -> cfg(t).getSpectatorSpawnPoint() != null, (t, l) -> cfg(t).setSpectatorSpawnPoint(l), "已设置旁观者出生点。"));
        steps.add(list("monster_spawn_points", "怪物生成点", "逐个添加怪物生成位置", Material.ZOMBIE_HEAD,
                t -> cfg(t).getMonsterSpawnPoints(), (t, l) -> cfg(t).setMonsterSpawnPoints(l)));
        steps.add(list("chicken_spawn_points", "鸡生成点", "逐个添加鸡生成位置", Material.EGG,
                t -> cfg(t).getChickenSpawnPoints(), (t, l) -> cfg(t).setChickenSpawnPoints(l)));
        steps.add(list("player_spawn_points", "玩家出生点", "逐个添加玩家出生位置", Material.PLAYER_HEAD,
                t -> cfg(t).getPlayerSpawnPoints(), (t, l) -> cfg(t).setPlayerSpawnPoints(l)));
        return steps;
    }
    private static TGTTOSConfig cfg(SetupTarget target) { return (TGTTOSConfig) target.config(); }
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

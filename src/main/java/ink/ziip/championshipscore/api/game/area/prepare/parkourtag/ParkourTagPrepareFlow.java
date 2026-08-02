package ink.ziip.championshipscore.api.game.area.prepare.parkourtag;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareFlowDefinition;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.SchematicStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StampStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagConfig;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagLayout;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Parkour Tag's prepare flow: the full schematic -> stamp -> set-every-point sequence. Copy 0 is pasted at
 * {@link ParkourTagLayout#FIRST} = (0,100,0); every other copy's anchors are derived from copy 0 at match
 * time, so the admin only configures copy 0. Each map definition owns its own physical world.
 */
public class ParkourTagPrepareFlow extends PrepareFlowDefinition {

    @Override
    public @NotNull String worldName(@NotNull SetupTarget target) {
        return target.worldName();
    }

    @Override
    public boolean isInCorrectWorld(@NotNull Player player, @NotNull SetupTarget target) {
        World w = player.getWorld();
        return w != null && target.worldName().equals(w.getName());
    }

    @Override
    public @NotNull Location copyZeroLocation(@NotNull SetupTarget target) {
        World w = Bukkit.getWorld(target.worldName());
        if (w == null) return CCConfig.LOBBY_LOCATION;
        return cfg(target).getCopyGrid().origin(0).toLocation(w);
    }

    @Override
    public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        List<PrepareStep> steps = new ArrayList<>();
        File schematic = new File(new File(new File(new File(target.plugin().getDataFolder(),
                "parkourtag"), "schematics"), target.name()), "arena.schem");

        steps.add(new ConfirmWorldStep(player -> isInCorrectWorld(player, target), target.worldName()));

        steps.add(new SchematicStep(plugin -> schematic,
                Component.text("保存场地模板"),
                Component.text("用 WorldEdit 选取整个场地后点击，保存为 arena.schem")));

        steps.add(StampStep.adaptive(plugin -> schematic,
                (a, size) -> cfg(a).prepareCopyGrid(size),
                (a, count) -> cfg(a).setCopyCount(count),
                (session, world) -> {
                    ParkourTagConfig previous = cfg(session.getTarget());
                    ArenaPreparer.clearCopies(session.getPlugin(), world, previous.getCopyGrid(),
                            previous.getCopyCount(), previous.getCopySize());
                }));

        steps.add(new WeSelectionStep("area_pos",
                Component.text("0 号模板边界"),
                Component.text("只选取 0 号场地；其他副本会自动平移"),
                Material.BEDROCK,
                a -> cfg(a).getAreaPos1() != null && cfg(a).getAreaPos2() != null,
                (a, sel) -> { cfg(a).setAreaPos1(sel[0]); cfg(a).setAreaPos2(sel[1]); },
                Utils.formatAdminSuccess("已设置场地总边界。")));

        steps.add(new StandAndRunStep("spectator_spawn",
                Component.text("旁观者出生点"),
                Component.text("站到旁观位置后点击"),
                Material.ENDER_EYE,
                a -> cfg(a).getSpectatorSpawnPoint() != null,
                (a, loc) -> cfg(a).setSpectatorSpawnPoint(loc),
                Utils.formatAdminSuccess("已设置旁观者出生点。")));

        steps.add(new StandAndRunStep("right_pre_spawn",
                Component.text("右侧预备点"),
                Component.text("站到右侧队伍预备位置后点击"),
                Material.GREEN_WOOL,
                a -> cfg(a).getRightPreSpawnPoint() != null,
                (a, loc) -> cfg(a).setRightPreSpawnPoint(loc),
                Utils.formatAdminSuccess("已设置右侧预备点。")));

        steps.add(new StandAndRunStep("left_pre_spawn",
                Component.text("左侧预备点"),
                Component.text("站到左侧队伍预备位置后点击"),
                Material.RED_WOOL,
                a -> cfg(a).getLeftPreSpawnPoint() != null,
                (a, loc) -> cfg(a).setLeftPreSpawnPoint(loc),
                Utils.formatAdminSuccess("已设置左侧预备点。")));

        steps.add(new WeSelectionStep("right_area_pos",
                Component.text("右侧场地边界"),
                Component.text("用 WorldEdit 选取右侧追逐区域"),
                Material.GREEN_STAINED_GLASS,
                a -> cfg(a).getRightAreaAreaPos1() != null && cfg(a).getRightAreaAreaPos2() != null,
                (a, sel) -> { cfg(a).setRightAreaAreaPos1(sel[0]); cfg(a).setRightAreaAreaPos2(sel[1]); },
                Utils.formatAdminSuccess("已设置右侧场地边界。")));

        steps.add(new WeSelectionStep("left_area_pos",
                Component.text("左侧场地边界"),
                Component.text("用 WorldEdit 选取左侧追逐区域"),
                Material.RED_STAINED_GLASS,
                a -> cfg(a).getLeftAreaAreaPos1() != null && cfg(a).getLeftAreaAreaPos2() != null,
                (a, sel) -> { cfg(a).setLeftAreaAreaPos1(sel[0]); cfg(a).setLeftAreaAreaPos2(sel[1]); },
                Utils.formatAdminSuccess("已设置左侧场地边界。")));

        steps.add(new StandAndRunStep("right_chaser_spawn",
                Component.text("右侧追击者出生点"),
                Component.text("站到右侧追击者出生位置后点击"),
                Material.GREEN_CONCRETE,
                a -> cfg(a).getRightAreaChaserSpawnPoint() != null,
                (a, loc) -> cfg(a).setRightAreaChaserSpawnPoint(loc),
                Utils.formatAdminSuccess("已设置右侧追击者出生点。")));

        steps.add(new StandAndRunStep("left_chaser_spawn",
                Component.text("左侧追击者出生点"),
                Component.text("站到左侧追击者出生位置后点击"),
                Material.RED_CONCRETE,
                a -> cfg(a).getLeftAreaChaserSpawnPoint() != null,
                (a, loc) -> cfg(a).setLeftAreaChaserSpawnPoint(loc),
                Utils.formatAdminSuccess("已设置左侧追击者出生点。")));

        steps.add(escapeeStep("right_escapee_spawns",
                Component.text("右侧逃生者出生点"),
                Component.text("逐个添加右侧逃生者出生位置"),
                Material.LIME_WOOL,
                ParkourTagConfig::getRightAreaEscapeeSpawnPoints,
                ParkourTagConfig::setRightAreaEscapeeSpawnPoints));

        steps.add(escapeeStep("left_escapee_spawns",
                Component.text("左侧逃生者出生点"),
                Component.text("逐个添加左侧逃生者出生位置"),
                Material.ORANGE_WOOL,
                ParkourTagConfig::getLeftAreaEscapeeSpawnPoints,
                ParkourTagConfig::setLeftAreaEscapeeSpawnPoints));

        return steps;
    }

    private static ParkourTagConfig cfg(SetupTarget target) {
        return (ParkourTagConfig) target.config();
    }

    private static PrepareStep escapeeStep(String key, Component name, Component desc, Material icon,
                                           java.util.function.Function<ParkourTagConfig, List<String>> getter,
                                           java.util.function.BiConsumer<ParkourTagConfig, List<String>> setter) {
        return new ListStep(key, name, desc, icon,
                a -> {
                    List<String> l = getter.apply(cfg(a));
                    return l == null || l.isEmpty();
                },
                (a, s) -> {
                    ParkourTagConfig c = cfg(a);
                    List<String> l = getter.apply(c);
                    if (l == null) l = new ArrayList<>();
                    l.add(s);
                    setter.accept(c, l);
                },
                a -> {
                    setter.accept(cfg(a), new ArrayList<>());
                },
                a -> {
                    List<String> l = getter.apply(cfg(a));
                    return l == null ? 0 : l.size();
                });
    }
}

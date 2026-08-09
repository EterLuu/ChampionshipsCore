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
import ink.ziip.championshipscore.api.game.config.GameSpawnResolver;
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
        Location spawn = GameSpawnResolver.resolve(target.config());
        if (spawn != null) return spawn;
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
                Component.text("保存双赛道对局模板"),
                Component.text("选取整个 0 号对局场地：两条镜像赛道、两队准备区及观战区")) {
            @Override
            public String capture(@NotNull ink.ziip.championshipscore.api.game.area.prepare.PrepareSession session,
                                  @NotNull Player player) {
                String result = super.capture(session, player);
                try {
                    Vector[] selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, true);
                    cfg(session.getTarget()).setAreaPos1(selection[0]);
                    cfg(session.getTarget()).setAreaPos2(selection[1]);
                } catch (Exception ignored) {
                    // The parent capture already returns the useful WorldEdit selection error.
                }
                return result;
            }
        });

        steps.add(StampStep.adaptiveKeepingSource(plugin -> schematic,
                (a, size) -> cfg(a).prepareCopyGrid(size),
                (a, count) -> cfg(a).setCopyCount(count),
                (session, world) -> {
                    ParkourTagConfig previous = cfg(session.getTarget());
                    ArenaPreparer.clearAdditionalCopies(session.getPlugin(), world,
                            previous.getCopyGrid(), previous.getCopyCount(), previous.getCopySize());
                }));

        steps.add(new WeSelectionStep("area_pos",
                Component.text("0 号对局场地总边界"),
                Component.text("框住两条赛道和两队准备区；其他对局副本会自动平移"),
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

        steps.add(new StandAndRunStep("right_prepare_spot",
                Component.text("对局位 A 队准备点"),
                Component.text("站到 A 队正式倒计时前集合的位置后点击"),
                Material.GREEN_WOOL,
                a -> cfg(a).getRightPrepareSpot() != null,
                (a, loc) -> cfg(a).setRightPrepareSpot(loc),
                Utils.formatAdminSuccess("已设置对局位 A 队准备点。")));

        steps.add(new StandAndRunStep("left_prepare_spot",
                Component.text("对局位 B 队准备点"),
                Component.text("站到 B 队正式倒计时前集合的位置后点击"),
                Material.RED_WOOL,
                a -> cfg(a).getLeftPrepareSpot() != null,
                (a, loc) -> cfg(a).setLeftPrepareSpot(loc),
                Utils.formatAdminSuccess("已设置对局位 B 队准备点。")));

        steps.add(new ParkourTagChaserButtonStep("right_chaser_button",
                Component.text("对局位 A 队追击者按钮"),
                Component.text("对准 A 队准备区内已经贴墙放置的按钮后点击"),
                a -> cfg(a).getRightChaserButton(),
                (a, loc) -> cfg(a).setRightChaserButton(loc)));

        steps.add(new ParkourTagChaserButtonStep("left_chaser_button",
                Component.text("对局位 B 队追击者按钮"),
                Component.text("对准 B 队准备区内已经贴墙放置的按钮后点击"),
                a -> cfg(a).getLeftChaserButton(),
                (a, loc) -> cfg(a).setLeftChaserButton(loc)));

        steps.add(new WeSelectionStep("right_area_pos",
                Component.text("赛道 1 完整边界（A 追 B 逃）"),
                Component.text("选取第一张完整跑酷地图；边界同时约束追击者和逃跑者"),
                Material.GREEN_STAINED_GLASS,
                a -> cfg(a).getRightAreaAreaPos1() != null && cfg(a).getRightAreaAreaPos2() != null,
                (a, sel) -> { cfg(a).setRightAreaAreaPos1(sel[0]); cfg(a).setRightAreaAreaPos2(sel[1]); },
                Utils.formatAdminSuccess("已设置赛道 1 完整边界。")));

        steps.add(new StandAndRunStep("right_chaser_spawn",
                Component.text("赛道 1：A 队追击者出生点"),
                Component.text("A 队追击者在赛道 1 的出生位置"),
                Material.GREEN_CONCRETE,
                a -> cfg(a).getRightAreaChaserSpawnPoint() != null,
                (a, loc) -> cfg(a).setRightAreaChaserSpawnPoint(loc),
                Utils.formatAdminSuccess("已设置赛道 1 的 A 队追击者出生点。")));

        steps.add(escapeeStep("right_escapee_spawns",
                Component.text("赛道 1：B 队逃跑者出生点"),
                Component.text("逐个添加 B 队逃跑者在赛道 1 的出生位置"),
                Material.LIME_WOOL,
                ParkourTagConfig::getRightAreaEscapeeSpawnPoints,
                ParkourTagConfig::setRightAreaEscapeeSpawnPoints));

        steps.add(new WeSelectionStep("left_area_pos",
                Component.text("赛道 2 完整边界（B 追 A 逃）"),
                Component.text("选取第二张完整跑酷地图；边界同时约束追击者和逃跑者"),
                Material.RED_STAINED_GLASS,
                a -> cfg(a).getLeftAreaAreaPos1() != null && cfg(a).getLeftAreaAreaPos2() != null,
                (a, sel) -> { cfg(a).setLeftAreaAreaPos1(sel[0]); cfg(a).setLeftAreaAreaPos2(sel[1]); },
                Utils.formatAdminSuccess("已设置赛道 2 完整边界。")));

        steps.add(new StandAndRunStep("left_chaser_spawn",
                Component.text("赛道 2：B 队追击者出生点"),
                Component.text("B 队追击者在赛道 2 的出生位置"),
                Material.RED_CONCRETE,
                a -> cfg(a).getLeftAreaChaserSpawnPoint() != null,
                (a, loc) -> cfg(a).setLeftAreaChaserSpawnPoint(loc),
                Utils.formatAdminSuccess("已设置赛道 2 的 B 队追击者出生点。")));

        steps.add(escapeeStep("left_escapee_spawns",
                Component.text("赛道 2：A 队逃跑者出生点"),
                Component.text("逐个添加 A 队逃跑者在赛道 2 的出生位置"),
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
                a -> getter.apply(cfg(a)),
                (a, values) -> setter.accept(cfg(a), values),
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

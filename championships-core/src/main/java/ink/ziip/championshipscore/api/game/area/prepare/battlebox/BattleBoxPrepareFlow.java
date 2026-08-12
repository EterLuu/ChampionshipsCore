package ink.ziip.championshipscore.api.game.area.prepare.battlebox;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

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
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxConfig;
import ink.ziip.championshipscore.api.game.config.GameSpawnResolver;
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
 * Battle Box's prepare flow: the full schematic -> stamp -> set-every-point sequence. Copy 0 remains the
 * hand-built source at its selected minimum corner; every other copy's anchors are derived from copy 0 at
 * match time, so the admin only configures copy 0. Each map definition owns its own physical world.
 */
public class BattleBoxPrepareFlow extends PrepareFlowDefinition {

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
                "battlebox"), "schematics"), target.name()), "arena.schem");

        steps.add(new ConfirmWorldStep(player -> isInCorrectWorld(player, target), target.worldName()));

        steps.add(new SchematicStep(plugin -> schematic,
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-001")),
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-002"))) {
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
                    BattleBoxConfig previous = cfg(session.getTarget());
                    ArenaPreparer.clearAdditionalCopies(session.getPlugin(), world,
                            previous.getCopyGrid(), previous.getCopyCount(), previous.getCopySize());
                }));

        steps.add(new WeSelectionStep("area_pos",
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-003")),
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-004")),
                Material.BEDROCK,
                a -> cfg(a).getAreaPos1() != null && cfg(a).getAreaPos2() != null,
                (a, sel) -> { cfg(a).setAreaPos1(sel[0]); cfg(a).setAreaPos2(sel[1]); },
                Utils.formatAdminSuccess(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-005"))));

        steps.add(new StandAndRunStep("spectator_spawn",
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-006")),
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-007")),
                Material.ENDER_EYE,
                a -> cfg(a).getSpectatorSpawnPoint() != null,
                (a, loc) -> cfg(a).setSpectatorSpawnPoint(loc),
                Utils.formatAdminSuccess(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-008"))));

        steps.add(new StandAndRunStep("right_spawn",
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-009")),
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-010")),
                Material.GREEN_WOOL,
                a -> cfg(a).getRightSpawnPoint() != null,
                (a, loc) -> cfg(a).setRightSpawnPoint(loc),
                Utils.formatAdminSuccess(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-011"))));

        steps.add(new StandAndRunStep("left_spawn",
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-012")),
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-013")),
                Material.RED_WOOL,
                a -> cfg(a).getLeftSpawnPoint() != null,
                (a, loc) -> cfg(a).setLeftSpawnPoint(loc),
                Utils.formatAdminSuccess(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-014"))));

        steps.add(new StandAndRunStep("right_prepare_spot",
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-015")),
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-016")),
                Material.GREEN_STAINED_GLASS,
                a -> cfg(a).getRightPrepareSpot() != null,
                (a, loc) -> cfg(a).setRightPrepareSpot(loc),
                Utils.formatAdminSuccess(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-017"))));

        steps.add(new StandAndRunStep("left_prepare_spot",
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-018")),
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-019")),
                Material.RED_STAINED_GLASS,
                a -> cfg(a).getLeftPrepareSpot() != null,
                (a, loc) -> cfg(a).setLeftPrepareSpot(loc),
                Utils.formatAdminSuccess(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-020"))));

        steps.add(new WeSelectionStep("wool_pos",
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-021")),
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-022")),
                Material.YELLOW_WOOL,
                a -> cfg(a).getWoolPos1() != null && cfg(a).getWoolPos2() != null,
                (a, sel) -> { cfg(a).setWoolPos1(sel[0]); cfg(a).setWoolPos2(sel[1]); },
                Utils.formatAdminSuccess(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-023"))));

        steps.add(new ListStep("potion_spawn_points",
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-024")),
                Component.text(GuiConfig.text("prepare-battlebox-battleboxprepareflow.text-025")),
                Material.LIME_WOOL,
                a -> cfg(a).getPotionSpawnPoints(),
                (a, values) -> cfg(a).setPotionSpawnPoints(values),
                a -> {
                    List<String> l = cfg(a).getPotionSpawnPoints();
                    return l == null || l.isEmpty();
                },
                (a, s) -> {
                    BattleBoxConfig c = cfg(a);
                    List<String> l = c.getPotionSpawnPoints();
                    if (l == null) l = new ArrayList<>();
                    l.add(s);
                    c.setPotionSpawnPoints(l);
                },
                a -> cfg(a).setPotionSpawnPoints(new ArrayList<>()),
                a -> {
                    List<String> l = cfg(a).getPotionSpawnPoints();
                    return l == null ? 0 : l.size();
                }));

        return steps;
    }

    private static BattleBoxConfig cfg(SetupTarget target) {
        return (BattleBoxConfig) target.config();
    }
}

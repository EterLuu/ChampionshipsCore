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
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.save-venue-template")),
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.use-worldedit-to-select-the-entire-venue-and-click-save-as-arena-schem"))) {
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
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.template-arena-boundary-name")),
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.frame-the-complete-hand-preserved-site-0-this-minimum-angle-determines-where-the-copy-will-be-pasted")),
                Material.BEDROCK,
                a -> cfg(a).getAreaPos1() != null && cfg(a).getAreaPos2() != null,
                (a, sel) -> { cfg(a).setAreaPos1(sel[0]); cfg(a).setAreaPos2(sel[1]); },
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.battle-box.setup.general-site-boundary-set"))));

        steps.add(new StandAndRunStep("spectator_spawn",
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.spectator-spawn-point")),
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.stand-in-a-spectator-position-and-click")),
                Material.ENDER_EYE,
                a -> cfg(a).getSpectatorSpawnPoint() != null,
                (a, loc) -> cfg(a).setSpectatorSpawnPoint(loc),
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.battle-box.setup.spectator-spawn-point-has-been-set"))));

        steps.add(new StandAndRunStep("right_spawn",
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.the-team-s-spawn-point-on-the-right")),
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.stand-at-the-birth-position-of-the-team-on-the-right-and-click")),
                Material.GREEN_WOOL,
                a -> cfg(a).getRightSpawnPoint() != null,
                (a, loc) -> cfg(a).setRightSpawnPoint(loc),
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.battle-box.setup.the-spawn-point-of-the-team-on-the-right-has-been-set"))));

        steps.add(new StandAndRunStep("left_spawn",
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.spawn-point-of-the-left-team")),
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.stand-at-the-birth-position-of-the-left-team-and-click")),
                Material.RED_WOOL,
                a -> cfg(a).getLeftSpawnPoint() != null,
                (a, loc) -> cfg(a).setLeftSpawnPoint(loc),
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.battle-box.setup.the-spawn-point-of-the-left-team-has-been-set"))));

        steps.add(new StandAndRunStep("right_prepare_spot",
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.right-team-ready-point")),
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.stand-at-the-team-s-ready-position-on-the-right-and-click")),
                Material.GREEN_STAINED_GLASS,
                a -> cfg(a).getRightPrepareSpot() != null,
                (a, loc) -> cfg(a).setRightPrepareSpot(loc),
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.battle-box.setup.right-team-ready-point-set"))));

        steps.add(new StandAndRunStep("left_prepare_spot",
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.left-team-ready-point")),
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.stand-at-the-team-s-ready-position-on-the-left-and-click")),
                Material.RED_STAINED_GLASS,
                a -> cfg(a).getLeftPrepareSpot() != null,
                (a, loc) -> cfg(a).setLeftPrepareSpot(loc),
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.battle-box.setup.left-team-ready-point-set"))));

        steps.add(new WeSelectionStep("wool_pos",
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.wool-area")),
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.use-worldedit-to-select-the-wool-placement-area")),
                Material.YELLOW_WOOL,
                a -> cfg(a).getWoolPos1() != null && cfg(a).getWoolPos2() != null,
                (a, sel) -> { cfg(a).setWoolPos1(sel[0]); cfg(a).setWoolPos2(sel[1]); },
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.battle-box.setup.wool-area-set"))));

        steps.add(new ListStep("potion_spawn_points",
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.potion-spawn-point")),
                Component.text(GuiConfig.text("map-editor.games.battle-box.setup.add-potion-spawn-locations-one-by-one")),
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

package ink.ziip.championshipscore.api.game.area.prepare.parkourtag;

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
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.save-the-two-track-match-template")),
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.template-match-arena-selection-hint"))) {
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
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.template-match-arena-boundary-name")),
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.frame-two-tracks-and-two-team-preparation-areas-other-game-copies-will-be-automatically-translated")),
                Material.BEDROCK,
                a -> cfg(a).getAreaPos1() != null && cfg(a).getAreaPos2() != null,
                (a, sel) -> { cfg(a).setAreaPos1(sel[0]); cfg(a).setAreaPos2(sel[1]); },
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.copy.general-site-boundary-set"))));

        steps.add(new StandAndRunStep("spectator_spawn",
                Component.text(GuiConfig.text("map-editor.copy.spectator-spawn-point")),
                Component.text(GuiConfig.text("map-editor.copy.stand-in-a-spectator-position-and-click")),
                Material.ENDER_EYE,
                a -> cfg(a).getSpectatorSpawnPoint() != null,
                (a, loc) -> cfg(a).setSpectatorSpawnPoint(loc),
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.copy.spectator-spawn-point-has-been-set"))));

        steps.add(new StandAndRunStep("right_prepare_spot",
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.prepare-points-for-team-a-in-position")),
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.stand-at-the-location-where-team-a-gathers-before-the-official-countdown-and-click")),
                Material.GREEN_WOOL,
                a -> cfg(a).getRightPrepareSpot() != null,
                (a, loc) -> cfg(a).setRightPrepareSpot(loc),
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.parkour-tag.setup.team-a-s-preparation-point-for-the-match-position-has-been-set"))));

        steps.add(new StandAndRunStep("left_prepare_spot",
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.prepare-points-for-team-b-in-position")),
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.stand-at-the-location-where-team-b-gathers-before-the-official-countdown-and-click")),
                Material.RED_WOOL,
                a -> cfg(a).getLeftPrepareSpot() != null,
                (a, loc) -> cfg(a).setLeftPrepareSpot(loc),
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.parkour-tag.setup.team-bs-preparation-point-for-the-match-position-has-been-set"))));

        steps.add(new ParkourTagChaserButtonStep("right_chaser_button",
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.team-a-chaser-button-for-position")),
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.aim-at-the-button-that-has-been-placed-on-the-wall-in-team-as-preparation-area-and-click")),
                a -> cfg(a).getRightChaserButton(),
                (a, loc) -> cfg(a).setRightChaserButton(loc)));

        steps.add(new ParkourTagChaserButtonStep("left_chaser_button",
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.chaser-button-for-team-b-in-position")),
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.aim-at-the-button-that-has-been-placed-on-the-wall-in-the-preparation-area-of-team-b-and-click")),
                a -> cfg(a).getLeftChaserButton(),
                (a, loc) -> cfg(a).setLeftChaserButton(loc)));

        steps.add(new WeSelectionStep("right_area_pos",
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.track-1-complete-boundary-a-chase-b-escape")),
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.select-the-first-complete-parkour-map-boundaries-restrain-pursuers-and-escapers-at-the-same-time")),
                Material.GREEN_STAINED_GLASS,
                a -> cfg(a).getRightAreaAreaPos1() != null && cfg(a).getRightAreaAreaPos2() != null,
                (a, sel) -> { cfg(a).setRightAreaAreaPos1(sel[0]); cfg(a).setRightAreaAreaPos2(sel[1]); },
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.parkour-tag.setup.track-1-complete-boundary-set"))));

        steps.add(new StandAndRunStep("right_chaser_spawn",
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.track-1-spawn-point-of-team-a-s-pursuers")),
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.the-spawn-position-of-team-a-s-pursuers-on-track-1")),
                Material.GREEN_CONCRETE,
                a -> cfg(a).getRightAreaChaserSpawnPoint() != null,
                (a, loc) -> cfg(a).setRightAreaChaserSpawnPoint(loc),
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.parkour-tag.setup.the-spawn-point-of-team-a-s-pursuers-on-track-1-has-been-set"))));

        steps.add(escapeeStep("right_escapee_spawns",
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.track-1-spawn-point-of-team-b-escapees")),
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.add-the-spawn-position-of-team-b-escapers-on-track-1-one-by-one")),
                Material.LIME_WOOL,
                ParkourTagConfig::getRightAreaEscapeeSpawnPoints,
                ParkourTagConfig::setRightAreaEscapeeSpawnPoints));

        steps.add(new WeSelectionStep("left_area_pos",
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.track-2-complete-boundary-b-chases-a-and-escapes")),
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.select-the-second-full-parkour-map-boundaries-restrain-pursuers-and-escapers-at-the-same-time")),
                Material.RED_STAINED_GLASS,
                a -> cfg(a).getLeftAreaAreaPos1() != null && cfg(a).getLeftAreaAreaPos2() != null,
                (a, sel) -> { cfg(a).setLeftAreaAreaPos1(sel[0]); cfg(a).setLeftAreaAreaPos2(sel[1]); },
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.parkour-tag.setup.track-2-complete-borders-set"))));

        steps.add(new StandAndRunStep("left_chaser_spawn",
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.track-2-team-b-chaser-spawn-point")),
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.the-spawn-position-of-team-b-chasers-on-track-2")),
                Material.RED_CONCRETE,
                a -> cfg(a).getLeftAreaChaserSpawnPoint() != null,
                (a, loc) -> cfg(a).setLeftAreaChaserSpawnPoint(loc),
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.parkour-tag.setup.the-spawn-point-of-team-b-chasers-on-track-2-has-been-set"))));

        steps.add(escapeeStep("left_escapee_spawns",
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.track-2-team-a-escapers-spawn-point")),
                Component.text(GuiConfig.text("map-editor.games.parkour-tag.setup.added-spawn-positions-of-team-a-escapees-on-track-2-one-by-one")),
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

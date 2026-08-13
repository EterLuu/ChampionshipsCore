package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.ListStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.area.prepare.tgttos.TGTTOSAreaTypeStep;
import ink.ziip.championshipscore.api.game.area.prepare.tgttos.TGTTOSSpawnAreaStep;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSConfig;
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
        Location spawn = GameSpawnResolver.resolve(target.config());
        if (spawn != null) return spawn;
        World world = Bukkit.getWorld(target.worldName());
        return world == null ? CCConfig.LOBBY_LOCATION : world.getSpawnLocation();
    }
    @Override public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        List<PrepareStep> steps = new ArrayList<>();
        steps.add(new ConfirmWorldStep(player -> isInCorrectWorld(player, target), target.worldName()));
        steps.add(new TGTTOSAreaTypeStep());
        steps.add(new WeSelectionStep("area_pos", Component.text(GuiConfig.text("map-editor.games.tgttos.setup.track-boundary")), Component.text(GuiConfig.text("map-editor.games.tgttos.setup.use-worldedit-to-select-the-complete-area-of-this-track")),
                Material.BEDROCK, t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                (t, v) -> { cfg(t).setAreaPos1(v[0]); cfg(t).setAreaPos2(v[1]); },
                Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.tgttos.setup.track-boundaries-set"))));
        steps.add(location("spectator_spawn", GuiConfig.text("map-editor.games.tgttos.setup.spectator-spawn-point"), Material.ENDER_EYE,
                t -> cfg(t).getSpectatorSpawnPoint() != null, (t, l) -> cfg(t).setSpectatorSpawnPoint(l), GuiConfig.text("map-editor.games.tgttos.setup.spectator-spawn-point-has-been-set")));
        steps.add(optionalList("monster_spawn_points", GuiConfig.text("map-editor.games.tgttos.setup.monster-spawn-point-optional"), GuiConfig.text("map-editor.games.tgttos.setup.add-monster-spawn-locations-one-by-one-if-left-blank-no-monsters-will-be-spawned-in-this-picture"), Material.ZOMBIE_HEAD,
                t -> cfg(t).getMonsterSpawnPoints(), (t, l) -> cfg(t).setMonsterSpawnPoints(l)));
        steps.add(new TGTTOSSpawnAreaStep("chicken_spawn_area", Component.text(GuiConfig.text("map-editor.games.tgttos.setup.chicken-spawning-area")),
                Component.text(GuiConfig.text("map-editor.games.tgttos.setup.use-worldedit-to-select-a-plane-one-block-high-chickens-are-randomly-generated-one-block-above-it")),
                Material.EGG, TGTTOSSpawnAreaStep.SpawnType.CHICKEN));
        steps.add(new TGTTOSSpawnAreaStep("player_spawn_area", Component.text(GuiConfig.text("map-editor.games.tgttos.setup.player-spawn-area-and-orientation")),
                Component.text(GuiConfig.text("map-editor.games.tgttos.setup.player-spawn-area-selection-hint")),
                Material.PLAYER_HEAD, TGTTOSSpawnAreaStep.SpawnType.PLAYER));
        return steps;
    }
    private static TGTTOSConfig cfg(SetupTarget target) { return (TGTTOSConfig) target.config(); }
    private static PrepareStep location(String key, String name, Material icon, java.util.function.Predicate<SetupTarget> set,
                                        java.util.function.BiConsumer<SetupTarget, Location> setter, String done) {
        return new StandAndRunStep(key, Component.text(name), Component.text(GuiConfig.text("map-editor.games.tgttos.setup.after-reaching-the-target-position-click")), icon, set, setter,
                Utils.formatAdminSuccess(done));
    }
    private static PrepareStep optionalList(String key, String name, String desc, Material icon,
                                            java.util.function.Function<SetupTarget, List<String>> getter,
                                            java.util.function.BiConsumer<SetupTarget, List<String>> setter) {
        return new ListStep(key, Component.text(name), Component.text(desc), icon,
                t -> values(getter.apply(t)), setter,
                t -> values(getter.apply(t)).isEmpty(),
                (t, value) -> { List<String> l = values(getter.apply(t)); l.add(value); setter.accept(t, l); },
                t -> setter.accept(t, new ArrayList<>()), t -> values(getter.apply(t)).size()) {
            @Override
            public boolean isSet(PrepareSession session) {
                return session != null;
            }

            @Override
            public String stateText(PrepareSession session) {
                return GuiConfig.text("map-editor.games.tgttos.setup.optional") + listCount(session) + GuiConfig.text("map-editor.games.tgttos.setup.item-count-suffix");
            }
        };
    }
    private static List<String> values(List<String> values) { return values == null ? new ArrayList<>() : new ArrayList<>(values); }
}

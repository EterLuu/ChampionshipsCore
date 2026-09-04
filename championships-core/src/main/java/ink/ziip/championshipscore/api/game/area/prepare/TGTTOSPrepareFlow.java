package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

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
        steps.add(new WeSelectionStep("area_pos", Component.text(GuiConfig.text("map-editor.menus.step-list.items.track-boundary.title")), Component.text(GuiConfig.line("map-editor.menus.step-list.items.track-boundary.lore", 0)),
                Material.BEDROCK, t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                (t, v) -> { cfg(t).setAreaPos1(v[0]); cfg(t).setAreaPos2(v[1]); },
                Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_STEP_TRACK_BOUNDARY_SET)));
        steps.add(location("spectator_spawn", GuiConfig.text("map-editor.menus.step-list.items.spectator-spawn.title"), Material.ENDER_EYE,
                t -> cfg(t).getSpectatorSpawnPoint() != null, (t, l) -> cfg(t).setSpectatorSpawnPoint(l), MessageConfig.MAP_EDITOR_STEP_SPECTATOR_SPAWN_POINT_SET));
        steps.add(optionalList("monster_spawn_points", GuiConfig.text("map-editor.menus.step-list.games.tgttos.items.monster-spawn.title"), GuiConfig.line("map-editor.menus.step-list.games.tgttos.items.monster-spawn.lore", 0), Material.ZOMBIE_HEAD,
                t -> cfg(t).getMonsterSpawnPoints(), (t, l) -> cfg(t).setMonsterSpawnPoints(l)));
        steps.add(new TGTTOSSpawnAreaStep("chicken_spawn_area", Component.text(GuiConfig.text("map-editor.menus.step-list.games.tgttos.items.chicken-spawn-area.title")),
                Component.text(GuiConfig.line("map-editor.menus.step-list.games.tgttos.items.chicken-spawn-area.lore", 0)),
                Material.EGG, TGTTOSSpawnAreaStep.SpawnType.CHICKEN));
        steps.add(new TGTTOSSpawnAreaStep("player_spawn_area", Component.text(GuiConfig.text("map-editor.menus.step-list.games.tgttos.items.player-spawn-area.title")),
                Component.text(GuiConfig.line("map-editor.menus.step-list.games.tgttos.items.player-spawn-area.lore", 0)),
                Material.PLAYER_HEAD, TGTTOSSpawnAreaStep.SpawnType.PLAYER));
        return steps;
    }
    private static TGTTOSConfig cfg(SetupTarget target) { return (TGTTOSConfig) target.config(); }
    private static PrepareStep location(String key, String name, Material icon, java.util.function.Predicate<SetupTarget> set,
                                        java.util.function.BiConsumer<SetupTarget, Location> setter, String done) {
        return new StandAndRunStep(key, Component.text(name), Component.text(GuiConfig.line("map-editor.menus.step-list.items.stand-run.lore", 0)), icon, set, setter,
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
                return GuiConfig.text("map-editor.menus.step-list.games.tgttos.items.monster-spawn.states.count.title", java.util.Map.of("count", listCount(session)));
            }
        };
    }
    private static List<String> values(List<String> values) { return values == null ? new ArrayList<>() : new ArrayList<>(values); }
}

package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.GuiText;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

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
        steps.add(selection("area", GuiConfig.text("map-editor.menus.step-list.games.dodgebolt.items.area.title"), Material.BEDROCK,
                t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                (t, v) -> { cfg(t).setAreaPos1(v[0]); cfg(t).setAreaPos2(v[1]); }));
        steps.add(selection("spectator_area", GuiConfig.text("map-editor.menus.step-list.games.dodgebolt.items.spectator-area.title"), Material.SPYGLASS,
                t -> cfg(t).getSpectatorAreaPos1() != null && cfg(t).getSpectatorAreaPos2() != null,
                (t, v) -> { cfg(t).setSpectatorAreaPos1(v[0]); cfg(t).setSpectatorAreaPos2(v[1]); }));
        steps.add(selection("platform", GuiConfig.text("map-editor.menus.step-list.games.dodgebolt.items.platform.title"), Material.SMOOTH_STONE,
                t -> cfg(t).getPlatformPos1() != null && cfg(t).getPlatformPos2() != null,
                (t, v) -> { cfg(t).setPlatformPos1(v[0]); cfg(t).setPlatformPos2(v[1]); }));
        steps.add(selection("right_area", GuiConfig.text("map-editor.menus.step-list.games.dodgebolt.items.right-area.title"), Material.RED_STAINED_GLASS,
                t -> cfg(t).getRightAreaPos1() != null && cfg(t).getRightAreaPos2() != null,
                (t, v) -> { cfg(t).setRightAreaPos1(v[0]); cfg(t).setRightAreaPos2(v[1]); }));
        steps.add(selection("left_area", GuiConfig.text("map-editor.menus.step-list.games.dodgebolt.items.left-area.title"), Material.BLUE_STAINED_GLASS,
                t -> cfg(t).getLeftAreaPos1() != null && cfg(t).getLeftAreaPos2() != null,
                (t, v) -> { cfg(t).setLeftAreaPos1(v[0]); cfg(t).setLeftAreaPos2(v[1]); }));
        steps.add(selection("right_shoot", GuiConfig.text("map-editor.menus.step-list.games.dodgebolt.items.right-shoot.title"), Material.RED_CONCRETE,
                t -> cfg(t).getRightShootPos1() != null && cfg(t).getRightShootPos2() != null,
                (t, v) -> { cfg(t).setRightShootPos1(v[0]); cfg(t).setRightShootPos2(v[1]); }));
        steps.add(selection("left_shoot", GuiConfig.text("map-editor.menus.step-list.games.dodgebolt.items.left-shoot.title"), Material.BLUE_CONCRETE,
                t -> cfg(t).getLeftShootPos1() != null && cfg(t).getLeftShootPos2() != null,
                (t, v) -> { cfg(t).setLeftShootPos1(v[0]); cfg(t).setLeftShootPos2(v[1]); }));
        steps.add(location("spectator_spawn", GuiConfig.text("map-editor.menus.step-list.items.spectator-spawn.title"), Material.ENDER_EYE,
                t -> cfg(t).getSpectatorSpawnPoint() != null,
                (t, l) -> cfg(t).setSpectatorSpawnPoint(l)));
        steps.add(list("right_spawns", GuiConfig.text("map-editor.menus.step-list.games.dodgebolt.items.right-spawn.title"), Material.RED_WOOL,
                t -> cfg(t).getRightSpawnPoints(), (t, l) -> cfg(t).setRightSpawnPoints(l)));
        steps.add(list("left_spawns", GuiConfig.text("map-editor.menus.step-list.games.dodgebolt.items.left-spawn.title"), Material.BLUE_WOOL,
                t -> cfg(t).getLeftSpawnPoints(), (t, l) -> cfg(t).setLeftSpawnPoints(l)));
        steps.add(location("right_arrow", GuiConfig.text("map-editor.menus.step-list.games.dodgebolt.items.right-arrow.title"), Material.ARROW,
                t -> hasPoint(cfg(t).getRightArrowSpawnPoint()),
                (t, l) -> cfg(t).setRightArrowSpawnPoint(Utils.getLocationConfigString(l))));
        steps.add(location("left_arrow", GuiConfig.text("map-editor.menus.step-list.games.dodgebolt.items.left-arrow.title"), Material.SPECTRAL_ARROW,
                t -> hasPoint(cfg(t).getLeftArrowSpawnPoint()),
                (t, l) -> cfg(t).setLeftArrowSpawnPoint(Utils.getLocationConfigString(l))));
        return steps;
    }

    @Override public @NotNull List<String> validate(@NotNull PrepareSession session) {
        List<String> errors = new ArrayList<>(super.validate(session));
        DodgeboltConfig config = cfg(session.getTarget());
        requireCount(errors, GuiConfig.text("map-editor.menus.step-list.games.dodgebolt.items.right-spawn.title"), config.getRightSpawnPoints(), 4);
        requireCount(errors, GuiConfig.text("map-editor.menus.step-list.games.dodgebolt.items.left-spawn.title"), config.getLeftSpawnPoints(), 4);
        requirePoint(errors, GuiConfig.text("map-editor.menus.step-list.games.dodgebolt.items.right-arrow.title"), config.getRightArrowSpawnPoint());
        requirePoint(errors, GuiConfig.text("map-editor.menus.step-list.games.dodgebolt.items.left-arrow.title"), config.getLeftArrowSpawnPoint());
        if (!isInSpectatorArea(config, config.getSpectatorSpawnPoint()))
            errors.add(MessageConfig.MAP_EDITOR_DODGEBOLT_SPECTATOR_INSIDE);
        if (isInArea(config, config.getSpectatorSpawnPoint()))
            errors.add(MessageConfig.MAP_EDITOR_DODGEBOLT_SPECTATOR_OUTSIDE);
        return errors;
    }

    private static WeSelectionStep selection(String key, String name, Material icon,
                                              java.util.function.Predicate<SetupTarget> set,
                                              java.util.function.BiConsumer<SetupTarget, Vector[]> setter) {
        return new WeSelectionStep(key, Component.text(name), Component.text(GuiConfig.line("map-editor.menus.step-list.games.dodgebolt.items."+key+".lore", 0)),
                icon, set, setter, Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_DODGEBOLT_STEP_SET.replace("%name%", name)));
    }

    private static StandAndRunStep location(String key, String name, Material icon,
                                            java.util.function.Predicate<SetupTarget> set,
                                            java.util.function.BiConsumer<SetupTarget, Location> setter) {
        return new StandAndRunStep(key, Component.text(name), Component.text(GuiConfig.line("map-editor.menus.step-list.items.stand-run.lore", 0)),
                icon, set, setter, Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_DODGEBOLT_STEP_SET.replace("%name%", name)));
    }

    private static ListStep list(String key, String name, Material icon,
                                 java.util.function.Function<SetupTarget, List<String>> getter,
                                 java.util.function.BiConsumer<SetupTarget, List<String>> setter) {
        return new ListStep(key, Component.text(name), Component.text(GuiConfig.line("map-editor.menus.step-list.games.dodgebolt.items."+key+".lore", 0)), icon,
                t -> values(getter.apply(t)), setter,
                t -> values(getter.apply(t)).isEmpty(),
                (t, value) -> { List<String> list = values(getter.apply(t)); list.add(value); setter.accept(t, list); },
                t -> setter.accept(t, new ArrayList<>()), t -> values(getter.apply(t)).size());
    }

    private static void requireCount(List<String> errors, String name, List<String> values, int minimum) {
        int count = values == null ? 0 : values.size();
        if (count < minimum) errors.add(MessageConfig.MAP_EDITOR_DODGEBOLT_COUNT_REQUIRED.replace("%name%", name).replace("%minimum%", String.valueOf(minimum)).replace("%count%", String.valueOf(count)));
    }

    private static void requirePoint(List<String> errors, String name, String value) {
        if (!hasPoint(value)) errors.add(MessageConfig.MAP_EDITOR_DODGEBOLT_POINT_UNSET.replace("%name%", name));
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

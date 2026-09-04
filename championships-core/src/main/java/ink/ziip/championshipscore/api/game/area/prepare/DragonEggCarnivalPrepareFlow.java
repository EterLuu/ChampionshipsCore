package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import ink.ziip.championshipscore.api.game.area.prepare.step.ConfirmWorldStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.StandAndRunStep;
import ink.ziip.championshipscore.api.game.area.prepare.step.WeSelectionStep;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalConfig;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Guided setup for one directly edited Dragon Egg Carnival map. */
public final class DragonEggCarnivalPrepareFlow extends SnapshotMapPrepareFlow {
    public DragonEggCarnivalPrepareFlow() {
        super(World.Environment.THE_END);
    }

    @Override public @NotNull List<PrepareStep> buildSteps(@NotNull SetupTarget target) {
        List<PrepareStep> steps = new ArrayList<>();
        steps.add(new ConfirmWorldStep(player -> isInCorrectWorld(player, target), target.worldName()));
        steps.add(new WeSelectionStep("area_pos", Component.text(GuiConfig.text("map-editor.menus.step-list.items.site-boundary.title")), Component.text(GuiConfig.line("map-editor.menus.step-list.items.site-boundary.lore", 0)),
                Material.BEDROCK, t -> cfg(t).getAreaPos1() != null && cfg(t).getAreaPos2() != null,
                (t, v) -> { cfg(t).setAreaPos1(v[0]); cfg(t).setAreaPos2(v[1]); }, Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_STEP_SITE_BOUNDARY_SET)));
        steps.add(location("spectator_spawn", GuiConfig.text("map-editor.menus.step-list.items.spectator-spawn.title"), Material.ENDER_EYE,
                t -> cfg(t).getSpectatorSpawnPoint() != null, (t, l) -> cfg(t).setSpectatorSpawnPoint(l), MessageConfig.MAP_EDITOR_STEP_SPECTATOR_SPAWN_POINT_SET));
        return steps;
    }

    @Override
    public @NotNull List<String> validate(@NotNull ink.ziip.championshipscore.api.game.area.prepare.PrepareSession session) {
        List<String> errors = new ArrayList<>(super.validate(session));
        World world = Bukkit.getWorld(session.getTarget().worldName());
        if (world != null && world.getEnvironment() != World.Environment.THE_END)
            errors.add(GuiConfig.line("MessageConfig.MAP_EDITOR_DEC_WORLD_ENVIRONMENT", 0));
        DragonEggCarnivalConfig config = cfg(session.getTarget());
        if (config.getAreaPos1() != null && config.getAreaPos2() != null
                && !coversRequiredFightRegion(config.getAreaPos1(), config.getAreaPos2()))
            errors.add(GuiConfig.line("MessageConfig.MAP_EDITOR_DEC_FIGHT_REGION", 0));
        Location spectator = config.getSpectatorSpawnPoint();
        if (spectator != null && spectator.getWorld() != null
                && !spectator.getWorld().getName().equals(session.getTarget().worldName()))
            errors.add(GuiConfig.line("MessageConfig.MAP_EDITOR_DEC_SPECTATOR_WORLD", 0));
        return errors;
    }

    static boolean coversRequiredFightRegion(@NotNull Vector first, @NotNull Vector second) {
        double minX = Math.min(first.getX(), second.getX());
        double maxX = Math.max(first.getX(), second.getX());
        double minY = Math.min(first.getY(), second.getY());
        double maxY = Math.max(first.getY(), second.getY());
        double minZ = Math.min(first.getZ(), second.getZ());
        double maxZ = Math.max(first.getZ(), second.getZ());
        return minX <= -104D && maxX >= 104D && minY <= 0D && maxY >= 128D
                && minZ <= -104D && maxZ >= 104D;
    }
    private static DragonEggCarnivalConfig cfg(SetupTarget target) { return (DragonEggCarnivalConfig) target.config(); }
    private static PrepareStep location(String key, String name, Material icon, java.util.function.Predicate<SetupTarget> set,
                                        java.util.function.BiConsumer<SetupTarget, Location> setter, String done) {
        return new StandAndRunStep(key, Component.text(name), Component.text(GuiConfig.line("map-editor.menus.step-list.items.stand-run.lore", 0)), icon, set, setter,
                Utils.formatAdminSuccess(done));
    }
}

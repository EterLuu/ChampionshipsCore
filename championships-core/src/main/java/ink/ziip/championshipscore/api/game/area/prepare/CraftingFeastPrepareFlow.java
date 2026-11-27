package ink.ziip.championshipscore.api.game.area.prepare;

import ink.ziip.championshipscore.api.game.area.prepare.step.*;
import ink.ziip.championshipscore.api.game.craftingfeast.config.CraftingFeastConfig;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.platform.bukkit.text.LegacyText;

import org.bukkit.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

/** Persistent restaurant worlds use the normal editor, schematic capture and publication gate. */
public final class CraftingFeastPrepareFlow extends PrepareFlowDefinition {
    private static CraftingFeastConfig config(SetupTarget target) {
        return (CraftingFeastConfig) target.config();
    }

    @Override
    public String worldName(SetupTarget target) {
        return target.worldName();
    }

    @Override
    public boolean isInCorrectWorld(Player player, SetupTarget target) {
        return target.worldName().equals(player.getWorld().getName());
    }

    @Override
    public Location copyZeroLocation(SetupTarget target) {
        return config(target).getSpawn();
    }

    @Override
    public List<PrepareStep> buildSteps(SetupTarget target) {
        String root = "map-editor.games.craftingfeast.menus.prepare.items.";
        List<PrepareStep> steps = new ArrayList<>();
        steps.add(new ConfirmWorldStep(p -> isInCorrectWorld(p, target), target.worldName()));
        steps.add(
                new SchematicStep(
                        p ->
                                new File(
                                        p.getDataFolder(),
                                        "craftingfeast/schematics/"
                                                + target.config().getConfigName()
                                                + ".schem"),
                        LegacyText.component(GuiConfig.text(root + "template.title")),
                        LegacyText.component(GuiConfig.line(root + "template.lore", 0))));
        steps.add(
                new StampStep(
                        p ->
                                new File(
                                        p.getDataFolder(),
                                        "craftingfeast/schematics/"
                                                + target.config().getConfigName()
                                                + ".schem"),
                        config(target).grid(),
                        (t, count) -> config(t).setRestaurants(count),
                        (t, size) -> config(t).setCopySize(size),
                        16));
        steps.add(
                point(
                        "spawn",
                        target,
                        Material.PLAYER_HEAD,
                        c -> c.getSpawn(),
                        (c, l) -> c.setSpawn(l)));
        steps.add(
                point(
                        "entrance",
                        target,
                        Material.OAK_DOOR,
                        c -> c.getEntrance(),
                        (c, l) -> c.setEntrance(l)));
        steps.add(
                point(
                        "aisle",
                        target,
                        Material.RED_CARPET,
                        c -> c.getAisle(),
                        (c, l) -> c.setAisle(l)));
        steps.add(
                point(
                        "order-counter",
                        target,
                        Material.LECTERN,
                        CraftingFeastConfig::getOrderCounter,
                        CraftingFeastConfig::setOrderCounter));
        steps.add(
                new ListStep(
                        "queue",
                        LegacyText.component(GuiConfig.text(root + "queue.title")),
                        LegacyText.component(GuiConfig.line(root + "queue.lore", 0)),
                        Material.BELL,
                        t -> config(t).getQueue(),
                        (t, list) -> config(t).setQueue(list),
                        t -> config(t).getQueue().size() >= CraftingFeastConfig.MAX_QUEUE,
                        (t, value) -> {
                            var list = new ArrayList<>(config(t).getQueue());
                            list.add(value);
                            config(t).setQueue(list);
                        },
                        t -> config(t).setQueue(new ArrayList<>()),
                        t -> config(t).getQueue().size()));
        steps.add(
                point(
                        "spectator",
                        target,
                        Material.ENDER_EYE,
                        c -> c.getSpectatorSpawnPoint(),
                        (c, l) -> c.setSpectatorSpawnPoint(l)));
        steps.add(
                new ListStep(
                        "seats",
                        LegacyText.component(GuiConfig.text(root + "seats.title")),
                        LegacyText.component(GuiConfig.line(root + "seats.lore", 0)),
                        Material.DARK_OAK_STAIRS,
                        t -> config(t).getSeats(),
                        (t, list) -> config(t).setSeats(list),
                        t -> config(t).getSeats().isEmpty(),
                        (t, value) -> {
                            var list = new ArrayList<>(config(t).getSeats());
                            list.add(value);
                            config(t).setSeats(list);
                        },
                        t -> config(t).setSeats(new ArrayList<>()),
                        t -> config(t).getSeats().size()));
        return steps;
    }

    private PrepareStep point(
            String id,
            SetupTarget target,
            Material material,
            java.util.function.Function<CraftingFeastConfig, Location> getter,
            java.util.function.BiConsumer<CraftingFeastConfig, Location> setter) {
        String key = "map-editor.games.craftingfeast.menus.prepare.items." + id;
        return new StandAndRunStep(
                id,
                LegacyText.component(GuiConfig.text(key + ".title")),
                LegacyText.component(GuiConfig.line(key + ".lore", 0)),
                material,
                t -> getter.apply(config(t)) != null,
                (t, location) -> setter.accept(config(t), location),
                GuiConfig.text(key + ".title"));
    }

    @Override
    public List<String> validate(PrepareSession session) {
        List<String> errors = new ArrayList<>(super.validate(session));
        try {
            config(session.getTarget()).validate();
        } catch (RuntimeException failure) {
            errors.add(failure.getMessage());
        }
        return errors;
    }
}

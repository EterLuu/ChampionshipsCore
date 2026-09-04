package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.config.message.GuiText;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorConfig;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorTeamArea;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Guided checkpoint editor for Parkour Warrior, including safe per-entry editing and deletion. */
public final class CheckpointListStep extends PrepareStep {
    public CheckpointListStep() {
        super("checkpoints", Component.text(GuiConfig.text("map-editor.menus.step-list.items.checkpoints.title")),
                Component.text(GuiConfig.line("map-editor.menus.step-list.items.checkpoints.lore", 0)),
                Material.LIME_CONCRETE, StepCaptureType.LIST);
    }

    private static ParkourWarriorConfig cfg(SetupTarget target) { return (ParkourWarriorConfig) target.config(); }

    private static void reload(PrepareSession session) {
        ParkourWarriorTeamArea area = session.getPlugin().getGameManager().getParkourWarriorManager()
                .getArea(session.getAreaName());
        if (area != null) area.loadCheckpoints();
    }

    @Override public boolean isSet(PrepareSession session) {
        return session != null && !cfg(session.getTarget()).ensureCheckpoints().getKeys(false).isEmpty();
    }

    @Override public boolean requiresWorldEdit() { return true; }

    @Override public String listAdd(@NotNull PrepareSession session, @NotNull Player player) {
        Vector[] selection;
        try {
            selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, false);
        } catch (Exception e) {
            return Utils.formatAdminError(MessageConfig.MAP_EDITOR_STEP_CHECKPOINT_SELECT_FIRST);
        }
        ConfigurationSection root = cfg(session.getTarget()).ensureCheckpoints();
        int order = orderedKeys(root).size() + 1;
        String key = "checkpoint" + order;
        while (root.isConfigurationSection(key)) key = "checkpoint" + (++order);
        ConfigurationSection checkpoint = root.createSection(key);
        checkpoint.set("order", order);
        checkpoint.set("name", key);
        checkpoint.set("type", "main");
        checkpoint.set("spawn", player.getLocation());
        checkpoint.set("enter.pos1", selection[0]);
        checkpoint.set("enter.pos2", selection[1]);
        checkpoint.createSection("sub-checkpoints");
        session.markDirty();
        reload(session);
        return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_STEP_CHECKPOINT_ADDED_CURRENT.replace("%count%", String.valueOf(root.getKeys(false).size())));
    }

    @Override public int listCount(@NotNull PrepareSession session) {
        return cfg(session.getTarget()).ensureCheckpoints().getKeys(false).size();
    }

    @Override
    public @NotNull List<ListEntry> listEntries(@NotNull PrepareSession session) {
        ConfigurationSection root = cfg(session.getTarget()).ensureCheckpoints();
        List<ListEntry> entries = new ArrayList<>();
        for (String key : orderedKeys(root)) {
            ConfigurationSection checkpoint = root.getConfigurationSection(key);
            if (checkpoint == null) continue;
            Map<String, String> placeholders = Map.of(
                    "order", String.valueOf(checkpoint.getInt("order")),
                    "name", checkpoint.getString("name", key),
                    "spawn", format(Utils.getLocation(checkpoint.getConfigurationSection("spawn"))),
                    "pos1", format(checkpoint.getVector("enter.pos1")),
                    "pos2", format(checkpoint.getVector("enter.pos2")));
            entries.add(new ListEntry(
                    GuiConfig.text("map-editor.menus.step-list.items.checkpoint-entry.title", placeholders),
                    List.of(
                            GuiConfig.line("map-editor.menus.step-list.items.checkpoint-entry.lore", 1, placeholders),
                            GuiConfig.line("map-editor.menus.step-list.items.checkpoint-entry.lore", 2, placeholders))));
        }
        return entries;
    }

    @Override
    public String listEdit(@NotNull PrepareSession session, @NotNull Player player, int index) {
        Vector[] selection;
        try {
            selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, false);
        } catch (Exception e) {
            return Utils.formatAdminError(MessageConfig.MAP_EDITOR_STEP_CHECKPOINT_RESELECT);
        }
        ConfigurationSection root = cfg(session.getTarget()).ensureCheckpoints();
        List<String> keys = orderedKeys(root);
        if (index < 0 || index >= keys.size()) return null;
        ConfigurationSection checkpoint = root.getConfigurationSection(keys.get(index));
        checkpoint.set("spawn", player.getLocation());
        checkpoint.set("enter.pos1", selection[0]);
        checkpoint.set("enter.pos2", selection[1]);
        session.markDirty();
        reload(session);
        return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_STEP_CHECKPOINT_UPDATED.replace("%order%", String.valueOf(index + 1)));
    }

    @Override
    public String listSetOrder(@NotNull PrepareSession session, @NotNull Player player,
                               int index, int newOrder) {
        ConfigurationSection root = cfg(session.getTarget()).ensureCheckpoints();
        List<String> keys = orderedKeys(root);
        if (index < 0 || index >= keys.size() || newOrder < 1 || newOrder > keys.size())
            return Utils.formatAdminError(MessageConfig.MAP_EDITOR_STEP_SERIAL_NUMBER_BETWEEN.replace("%max%", String.valueOf(keys.size())));
        String moved = keys.remove(index);
        keys.add(newOrder - 1, moved);
        renumber(root, keys);
        session.markDirty();
        reload(session);
        return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_STEP_CHECKPOINT_ADJUSTED_TO.replace("%order%", String.valueOf(newOrder)));
    }

    @Override
    public String listRemove(@NotNull PrepareSession session, @NotNull Player player, int index) {
        ConfigurationSection root = cfg(session.getTarget()).ensureCheckpoints();
        List<String> keys = orderedKeys(root);
        if (index < 0 || index >= keys.size()) return null;
        root.set(keys.get(index), null);
        keys.remove(index);
        renumber(root, keys);
        session.markDirty();
        reload(session);
        return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_STEP_CHECKPOINT_DELETED.replace("%order%", String.valueOf(index + 1)));
    }

    private static List<String> orderedKeys(ConfigurationSection root) {
        List<String> keys = new ArrayList<>(root.getKeys(false));
        keys.sort(Comparator.comparingInt(key -> root.getConfigurationSection(key) == null
                ? Integer.MAX_VALUE : root.getConfigurationSection(key).getInt("order", Integer.MAX_VALUE)));
        return keys;
    }

    private static void renumber(ConfigurationSection root, List<String> keys) {
        for (int i = 0; i < keys.size(); i++) root.getConfigurationSection(keys.get(i)).set("order", i + 1);
    }

    private static String format(Vector vector) {
        return vector == null ? GuiConfig.text("map-editor.menus.step-list.items.status.states.unset.title")
                : GuiText.coordinate(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
    }

    private static String format(Location location) {
        return location == null ? GuiConfig.text("map-editor.menus.step-list.items.status.states.unset.title")
                : GuiText.coordinate(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override public @NotNull Component listAddLabel() { return Component.text(GuiConfig.text("map-editor.menus.step-list.items.checkpoint-add.title")); }
    @Override public @NotNull Component listAddHint() { return Component.text(GuiConfig.line("map-editor.menus.step-list.items.checkpoint-add.lore", 0)); }
}

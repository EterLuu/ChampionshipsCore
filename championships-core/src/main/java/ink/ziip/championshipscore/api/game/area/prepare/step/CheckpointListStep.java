package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

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

/** Guided checkpoint editor for Parkour Warrior, including safe per-entry editing and deletion. */
public final class CheckpointListStep extends PrepareStep {
    public CheckpointListStep() {
        super("checkpoints", Component.text(GuiConfig.text("prepare-step-checkpointliststep.text-001")),
                Component.text(GuiConfig.text("prepare-step-checkpointliststep.text-002")),
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
            return Utils.formatAdminError(GuiConfig.text("prepare-step-checkpointliststep.text-003"));
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
        return Utils.formatAdminSuccess(GuiConfig.text("prepare-step-checkpointliststep.text-004") + root.getKeys(false).size() + GuiConfig.text("prepare-step-checkpointliststep.text-005"));
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
            entries.add(new ListEntry(GuiConfig.text("prepare-step-checkpointliststep.text-006") + checkpoint.getInt("order") + GuiConfig.text("common.separator")
                    + checkpoint.getString("name", key), List.of(
                    GuiConfig.text("prepare-step-checkpointliststep.text-007") + format(Utils.getLocation(checkpoint.getConfigurationSection("spawn"))),
                    GuiConfig.text("prepare-step-checkpointliststep.text-008") + format(checkpoint.getVector("enter.pos1")) + " → "
                            + format(checkpoint.getVector("enter.pos2")))));
        }
        return entries;
    }

    @Override
    public String listEdit(@NotNull PrepareSession session, @NotNull Player player, int index) {
        Vector[] selection;
        try {
            selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, false);
        } catch (Exception e) {
            return Utils.formatAdminError(GuiConfig.text("prepare-step-checkpointliststep.text-009"));
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
        return Utils.formatAdminSuccess(GuiConfig.text("prepare-step-checkpointliststep.text-010") + (index + 1) + GuiConfig.text("prepare-step-checkpointliststep.text-011"));
    }

    @Override
    public String listSetOrder(@NotNull PrepareSession session, @NotNull Player player,
                               int index, int newOrder) {
        ConfigurationSection root = cfg(session.getTarget()).ensureCheckpoints();
        List<String> keys = orderedKeys(root);
        if (index < 0 || index >= keys.size() || newOrder < 1 || newOrder > keys.size())
            return Utils.formatAdminError(GuiConfig.text("prepare-step-checkpointliststep.text-012") + keys.size() + GuiConfig.text("prepare-step-checkpointliststep.text-013"));
        String moved = keys.remove(index);
        keys.add(newOrder - 1, moved);
        renumber(root, keys);
        session.markDirty();
        reload(session);
        return Utils.formatAdminSuccess(GuiConfig.text("prepare-step-checkpointliststep.text-014") + newOrder + GuiConfig.text("prepare-step-checkpointliststep.text-015"));
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
        return Utils.formatAdminSuccess(GuiConfig.text("prepare-step-checkpointliststep.text-016") + (index + 1) + GuiConfig.text("prepare-step-checkpointliststep.text-017"));
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
        return vector == null ? GuiConfig.text("prepare-step-checkpointliststep.text-018") : vector.getBlockX() + ", " + vector.getBlockY() + ", " + vector.getBlockZ();
    }

    private static String format(Location location) {
        return location == null ? GuiConfig.text("prepare-step-checkpointliststep.text-018") : location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    @Override public @NotNull Component listAddLabel() { return Component.text(GuiConfig.text("prepare-step-checkpointliststep.text-019")); }
    @Override public @NotNull Component listAddHint() { return Component.text(GuiConfig.text("prepare-step-checkpointliststep.text-020")); }
}

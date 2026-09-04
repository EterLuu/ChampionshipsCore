package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;
import ink.ziip.championshipscore.configuration.config.message.GuiText;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import ink.ziip.championshipscore.api.game.acerace.AceRaceArea;
import ink.ziip.championshipscore.api.game.acerace.AceRaceConfig;
import ink.ziip.championshipscore.api.game.acerace.AceRaceEquipment;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AceRaceEquipmentGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AnvilInputGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.ListStepGui;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Adds and safely edits ordered WorldEdit progress gates and their following segment rules. */
public final class AceRaceProgressPointListStep extends PrepareStep {
    public AceRaceProgressPointListStep() {
        super("progress_points", Component.text(GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.progress-points.title")),
                Component.text(GuiConfig.line("map-editor.menus.step-list.games.ace-race.items.progress-points.lore", 0)),
                Material.LIME_CONCRETE, StepCaptureType.LIST);
    }

    private static AceRaceConfig cfg(SetupTarget target) {
        return (AceRaceConfig) target.config();
    }

    private static void reload(PrepareSession session) {
        AceRaceArea area = session.getPlugin().getGameManager().getAceRaceManager().getArea(session.getAreaName());
        if (area != null) area.loadCoursePoints();
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null && !cfg(session.getTarget()).ensureProgressPoints().getKeys(false).isEmpty();
    }

    @Override
    public boolean requiresWorldEdit() {
        return true;
    }

    @Override
    public String listAdd(@NotNull PrepareSession session, @NotNull Player player) {
        Gate gate = captureGate(session, player);
        if (gate == null) return null;
        int defaultFallY = gate.pos1().getBlockY();
        Utils.sendAdminInfo(player, MessageConfig.MAP_EDITOR_ACE_PROGRESS_FALL_INPUT.replace("%fall%", String.valueOf(defaultFallY)));
        AnvilInputGui.openInteger(player, GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.progress-fall-height.title"), defaultFallY, fallY ->
                AceRaceEquipmentGui.open(player, session, AceRaceEquipment.NONE, equipment -> {
                    ConfigurationSection root = cfg(session.getTarget()).ensureProgressPoints();
                    int order = orderedKeys(root).size() + 1;
                    int keyIndex = order;
                    String key = "progresspoint" + keyIndex;
                    while (root.isConfigurationSection(key)) key = "progresspoint" + (++keyIndex);
                    ConfigurationSection progressPoint = root.createSection(key);
                    writeProgressPoint(progressPoint, order, gate, fallY, equipment);
                    session.markDirty();
                    reload(session);
                    Utils.sendAdminSuccess(player, MessageConfig.MAP_EDITOR_ACE_PROGRESS_ADDED
                            .replace("%order%", String.valueOf(order)).replace("%fall%", String.valueOf(fallY))
                            .replace("%equipment%", equipment.displayName()));
                }));
        return null;
    }

    @Override
    public int listCount(@NotNull PrepareSession session) {
        return cfg(session.getTarget()).ensureProgressPoints().getKeys(false).size();
    }

    @Override
    public @NotNull List<ListEntry> listEntries(@NotNull PrepareSession session) {
        ConfigurationSection root = cfg(session.getTarget()).ensureProgressPoints();
        List<ListEntry> entries = new ArrayList<>();
        for (String key : orderedKeys(root)) {
            ConfigurationSection progressPoint = root.getConfigurationSection(key);
            if (progressPoint == null) continue;
            AceRaceEquipment equipment = AceRaceEquipment.fromConfig(progressPoint.getString("equipment"));
            Map<String, String> placeholders = Map.of(
                    "order", String.valueOf(progressPoint.getInt("order")),
                    "pos1", format(progressPoint.getVector("pos1")),
                    "pos2", format(progressPoint.getVector("pos2")),
                    "fall", String.valueOf(progressPoint.getInt("fall-y")),
                    "equipment", equipment.displayName());
            entries.add(new ListEntry(
                    GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.progress-entry.title", placeholders),
                    List.of(
                            GuiConfig.line("map-editor.menus.step-list.games.ace-race.items.progress-entry.lore", 0, placeholders),
                            GuiConfig.line("map-editor.menus.step-list.games.ace-race.items.progress-entry.lore", 1),
                            GuiConfig.line("map-editor.menus.step-list.games.ace-race.items.progress-entry.lore", 2, placeholders),
                            GuiConfig.line("map-editor.menus.step-list.games.ace-race.items.progress-entry.lore", 3, placeholders))));
        }
        return entries;
    }

    @Override
    public String listEdit(@NotNull PrepareSession session, @NotNull Player player, int index) {
        ConfigurationSection root = cfg(session.getTarget()).ensureProgressPoints();
        List<String> keys = orderedKeys(root);
        if (index < 0 || index >= keys.size()) return null;
        Gate gate = captureGate(session, player);
        if (gate == null) return null;
        String key = keys.get(index);
        ConfigurationSection existing = root.getConfigurationSection(key);
        if (existing == null) return null;
        int order = existing.getInt("order", index + 1);
        int defaultFallY = existing.getInt("fall-y", gate.pos1().getBlockY());
        AceRaceEquipment current = AceRaceEquipment.fromConfig(existing.getString("equipment"));
        Utils.sendAdminInfo(player, MessageConfig.MAP_EDITOR_ACE_PROGRESS_EDIT_INPUT.replace("%fall%", String.valueOf(defaultFallY)));
        AnvilInputGui.openInteger(player, GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.progress-fall-height.title"), defaultFallY, fallY ->
                AceRaceEquipmentGui.open(player, session, current, equipment -> {
                    ConfigurationSection progressPoint = cfg(session.getTarget()).ensureProgressPoints()
                            .getConfigurationSection(key);
                    if (progressPoint == null) {
                        Utils.sendAdminError(player, MessageConfig.MAP_EDITOR_ACE_PROGRESS_MISSING);
                        return;
                    }
                    writeProgressPoint(progressPoint, order, gate, fallY, equipment);
                    session.markDirty();
                    reload(session);
                    Utils.sendAdminSuccess(player, MessageConfig.MAP_EDITOR_ACE_PROGRESS_UPDATED
                            .replace("%order%", String.valueOf(order)).replace("%fall%", String.valueOf(fallY))
                            .replace("%equipment%", equipment.displayName()));
                }));
        return null;
    }

    @Override
    public boolean listEditHandlesNavigation() {
        return true;
    }

    public @NotNull String equipmentText(@NotNull PrepareSession session, int index) {
        ConfigurationSection progressPoint = progressPoint(session, index);
        if (progressPoint == null) return GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.progress-binding.states.missing.title");
        return GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.progress-binding.states.current.title")
                .replace("%equipment%", AceRaceEquipment.fromConfig(progressPoint.getString("equipment")).displayName());
    }

    /** Edits only the following segment's equipment, preserving the gate and fall height. */
    public void editEquipment(@NotNull PrepareSession session, @NotNull Player player, int index) {
        ConfigurationSection progressPoint = progressPoint(session, index);
        if (progressPoint == null) {
            Utils.sendAdminError(player, MessageConfig.MAP_EDITOR_ACE_PROGRESS_MISSING);
            return;
        }
        AceRaceEquipment current = AceRaceEquipment.fromConfig(progressPoint.getString("equipment"));
        AceRaceEquipmentGui.open(player, session, current, equipment -> {
            ConfigurationSection selected = progressPoint(session, index);
            if (selected == null) {
                Utils.sendAdminError(player, MessageConfig.MAP_EDITOR_ACE_PROGRESS_MISSING);
                return;
            }
            selected.set("equipment", equipment.configValue());
            session.markDirty();
            reload(session);
            Utils.sendAdminSuccess(player, MessageConfig.MAP_EDITOR_ACE_PROGRESS_EQUIPMENT_UPDATED
                    .replace("%order%", String.valueOf(selected.getInt("order", index + 1)))
                    .replace("%equipment%", equipment.displayName()));
            ListStepGui.openEdit(player, session, this, index);
        });
    }

    @Override
    public String listSetOrder(@NotNull PrepareSession session, @NotNull Player player,
                               int index, int newOrder) {
        ConfigurationSection root = cfg(session.getTarget()).ensureProgressPoints();
        List<String> keys = orderedKeys(root);
        if (index < 0 || index >= keys.size() || newOrder < 1 || newOrder > keys.size())
            return Utils.formatAdminError(MessageConfig.MAP_EDITOR_STEP_SERIAL_NUMBER_BETWEEN.replace("%max%", String.valueOf(keys.size())));
        String moved = keys.remove(index);
        keys.add(newOrder - 1, moved);
        renumber(root, keys);
        session.markDirty();
        reload(session);
        return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_ACE_PROGRESS_ADJUSTED.replace("%order%", String.valueOf(newOrder)));
    }

    @Override
    public String listRemove(@NotNull PrepareSession session, @NotNull Player player, int index) {
        ConfigurationSection root = cfg(session.getTarget()).ensureProgressPoints();
        List<String> keys = orderedKeys(root);
        if (index < 0 || index >= keys.size()) return null;
        root.set(keys.get(index), null);
        keys.remove(index);
        renumber(root, keys);
        session.markDirty();
        reload(session);
        return Utils.formatAdminSuccess(MessageConfig.MAP_EDITOR_ACE_PROGRESS_DELETED.replace("%index%", String.valueOf(index + 1)));
    }

    private static Gate captureGate(@NotNull PrepareSession session, @NotNull Player player) {
        Vector[] selection;
        try {
            selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, true);
        } catch (Exception exception) {
            Utils.sendAdminError(player, MessageConfig.MAP_EDITOR_ACE_PROGRESS_SELECT_FIRST);
            return null;
        }
        int spanX = Math.abs(selection[0].getBlockX() - selection[1].getBlockX());
        int spanY = Math.abs(selection[0].getBlockY() - selection[1].getBlockY());
        int spanZ = Math.abs(selection[0].getBlockZ() - selection[1].getBlockZ());
        if (spanY != 0 || (spanX > 0 && spanZ > 0) || (spanX == 0 && spanZ == 0)) {
            Utils.sendAdminError(player, MessageConfig.MAP_EDITOR_ACE_PROGRESS_LINE_INVALID);
            return null;
        }
        return new Gate(selection[0].clone(), selection[1].clone());
    }

    private static void writeProgressPoint(@NotNull ConfigurationSection progressPoint, int order,
                                           @NotNull Gate gate, int fallY,
                                           @NotNull AceRaceEquipment equipment) {
        progressPoint.set("order", order);
        progressPoint.set("pos1", gate.pos1());
        progressPoint.set("pos2", gate.pos2());
        progressPoint.set("fall-y", fallY);
        progressPoint.set("equipment", equipment.configValue());
        progressPoint.set("block", null);
    }

    private static List<String> orderedKeys(ConfigurationSection root) {
        List<String> keys = new ArrayList<>(root.getKeys(false));
        keys.sort(Comparator.comparingInt(key -> root.getConfigurationSection(key) == null
                ? Integer.MAX_VALUE : root.getConfigurationSection(key).getInt("order", Integer.MAX_VALUE)));
        return keys;
    }

    private static ConfigurationSection progressPoint(@NotNull PrepareSession session, int index) {
        ConfigurationSection root = cfg(session.getTarget()).ensureProgressPoints();
        List<String> keys = orderedKeys(root);
        if (index < 0 || index >= keys.size()) return null;
        return root.getConfigurationSection(keys.get(index));
    }

    private static void renumber(ConfigurationSection root, List<String> keys) {
        for (int i = 0; i < keys.size(); i++) root.getConfigurationSection(keys.get(i)).set("order", i + 1);
    }

    private static String format(Vector vector) {
        return vector == null ? GuiConfig.text("map-editor.menus.step-list.items.status.states.unset.title")
                : GuiText.coordinate(vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
    }

    @Override
    public @NotNull Component listAddLabel() {
        return Component.text(GuiConfig.text("map-editor.menus.step-list.games.ace-race.items.progress-add.title"));
    }

    @Override
    public @NotNull Component listAddHint() {
        return Component.text(GuiConfig.line("map-editor.menus.step-list.games.ace-race.items.progress-add.lore", 0));
    }

    private record Gate(@NotNull Vector pos1, @NotNull Vector pos2) {
    }
}

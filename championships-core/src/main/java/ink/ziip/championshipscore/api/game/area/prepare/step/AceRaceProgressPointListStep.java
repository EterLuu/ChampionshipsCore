package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.configuration.config.message.GuiConfig;

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

/** Adds and safely edits ordered WorldEdit progress gates and their following segment rules. */
public final class AceRaceProgressPointListStep extends PrepareStep {
    public AceRaceProgressPointListStep() {
        super("progress_points", Component.text(GuiConfig.text("map-editor.games.ace-race.steps.progress-points.racing-progress-points")),
                Component.text(GuiConfig.text("map-editor.games.ace-race.steps.progress-points.progress-line-selection-hint")),
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
        Utils.sendAdminInfo(player, GuiConfig.text("map-editor.games.ace-race.steps.progress-points.progress-point-fall-height-input-hint") + defaultFallY + "。");
        AnvilInputGui.openInteger(player, GuiConfig.text("map-editor.games.ace-race.steps.progress-points.enter-drop-height"), defaultFallY, fallY ->
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
                    Utils.sendAdminSuccess(player, GuiConfig.text("map-editor.games.ace-race.steps.progress-points.progress-points-added") + order + GuiConfig.text("map-editor.games.ace-race.steps.progress-points.drop-height")
                            + fallY + GuiConfig.text("map-editor.games.ace-race.steps.progress-points.stage-equipment") + equipment.displayName());
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
            entries.add(new ListEntry(GuiConfig.text("map-editor.games.ace-race.steps.progress-points.progress-point") + progressPoint.getInt("order"), List.of(
                    GuiConfig.text("map-editor.games.ace-race.steps.progress-points.line-selection") + format(progressPoint.getVector("pos1")) + " -> " + format(progressPoint.getVector("pos2")),
                    GuiConfig.text("map-editor.games.ace-race.steps.progress-points.trigger-range-automatically-expands-to-both-sides-if-less-than-20-cells-select-line-y-3-and-above"),
                    GuiConfig.text("map-editor.games.ace-race.steps.progress-points.fall-height-label") + progressPoint.getInt("fall-y"),
                    GuiConfig.text("map-editor.games.ace-race.steps.progress-points.equipment-for-the-next-stage") + equipment.displayName())));
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
        Utils.sendAdminInfo(player, GuiConfig.text("map-editor.games.ace-race.steps.progress-points.please-enter-the-fall-height-after-this-progress-point-leave-blank-to-retain-the-current-value") + defaultFallY + "。");
        AnvilInputGui.openInteger(player, GuiConfig.text("map-editor.games.ace-race.steps.progress-points.enter-drop-height"), defaultFallY, fallY ->
                AceRaceEquipmentGui.open(player, session, current, equipment -> {
                    ConfigurationSection progressPoint = cfg(session.getTarget()).ensureProgressPoints()
                            .getConfigurationSection(key);
                    if (progressPoint == null) {
                        Utils.sendAdminError(player, GuiConfig.text("map-editor.games.ace-race.steps.progress-points.this-progress-point-no-longer-exists"));
                        return;
                    }
                    writeProgressPoint(progressPoint, order, gate, fallY, equipment);
                    session.markDirty();
                    reload(session);
                    Utils.sendAdminSuccess(player, GuiConfig.text("map-editor.games.ace-race.steps.progress-points.progress-points-updated") + order + GuiConfig.text("map-editor.games.ace-race.steps.progress-points.drop-height")
                            + fallY + GuiConfig.text("map-editor.games.ace-race.steps.progress-points.stage-equipment") + equipment.displayName());
                }));
        return null;
    }

    @Override
    public boolean listEditHandlesNavigation() {
        return true;
    }

    public @NotNull String equipmentText(@NotNull PrepareSession session, int index) {
        ConfigurationSection progressPoint = progressPoint(session, index);
        if (progressPoint == null) return GuiConfig.text("map-editor.games.ace-race.steps.progress-points.current-prop-entry-does-not-exist");
        return GuiConfig.text("map-editor.games.ace-race.steps.progress-points.current-equipment") + AceRaceEquipment.fromConfig(
                progressPoint.getString("equipment")).displayName();
    }

    /** Edits only the following segment's equipment, preserving the gate and fall height. */
    public void editEquipment(@NotNull PrepareSession session, @NotNull Player player, int index) {
        ConfigurationSection progressPoint = progressPoint(session, index);
        if (progressPoint == null) {
            Utils.sendAdminError(player, GuiConfig.text("map-editor.games.ace-race.steps.progress-points.this-progress-point-no-longer-exists"));
            return;
        }
        AceRaceEquipment current = AceRaceEquipment.fromConfig(progressPoint.getString("equipment"));
        AceRaceEquipmentGui.open(player, session, current, equipment -> {
            ConfigurationSection selected = progressPoint(session, index);
            if (selected == null) {
                Utils.sendAdminError(player, GuiConfig.text("map-editor.games.ace-race.steps.progress-points.this-progress-point-no-longer-exists"));
                return;
            }
            selected.set("equipment", equipment.configValue());
            session.markDirty();
            reload(session);
            Utils.sendAdminSuccess(player, GuiConfig.text("map-editor.games.ace-race.steps.progress-points.progress-point-has-been-reached") + selected.getInt("order", index + 1)
                    + GuiConfig.text("map-editor.games.ace-race.steps.progress-points.the-equipment-for-the-next-stage-are-changed-to") + equipment.displayName());
            ListStepGui.openEdit(player, session, this, index);
        });
    }

    @Override
    public String listSetOrder(@NotNull PrepareSession session, @NotNull Player player,
                               int index, int newOrder) {
        ConfigurationSection root = cfg(session.getTarget()).ensureProgressPoints();
        List<String> keys = orderedKeys(root);
        if (index < 0 || index >= keys.size() || newOrder < 1 || newOrder > keys.size())
            return Utils.formatAdminError(GuiConfig.text("map-editor.games.ace-race.steps.progress-points.the-serial-number-must-be-between-1-and") + keys.size() + GuiConfig.text("map-editor.games.ace-race.steps.progress-points.range-end-suffix"));
        String moved = keys.remove(index);
        keys.add(newOrder - 1, moved);
        renumber(root, keys);
        session.markDirty();
        reload(session);
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.ace-race.steps.progress-points.the-progress-point-has-been-adjusted-to-the") + newOrder + GuiConfig.text("map-editor.games.ace-race.steps.progress-points.item-suffix"));
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
        return Utils.formatAdminSuccess(GuiConfig.text("map-editor.games.ace-race.steps.progress-points.deleted") + (index + 1) + GuiConfig.text("map-editor.games.ace-race.steps.progress-points.progress-points"));
    }

    private static Gate captureGate(@NotNull PrepareSession session, @NotNull Player player) {
        Vector[] selection;
        try {
            selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, true);
        } catch (Exception exception) {
            Utils.sendAdminError(player, GuiConfig.text("map-editor.games.ace-race.steps.progress-points.please-use-worldedit-to-select-the-horizontal-straight-line-of-the-progress-point-first"));
            return null;
        }
        int spanX = Math.abs(selection[0].getBlockX() - selection[1].getBlockX());
        int spanY = Math.abs(selection[0].getBlockY() - selection[1].getBlockY());
        int spanZ = Math.abs(selection[0].getBlockZ() - selection[1].getBlockZ());
        if (spanY != 0 || (spanX > 0 && spanZ > 0) || (spanX == 0 && spanZ == 0)) {
            Utils.sendAdminError(player, GuiConfig.text("map-editor.games.ace-race.steps.progress-points.progress-points-must-be-the-same-height-a-straight-worldedit-line-extending-in-the-x-or-z-direction"));
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
        return vector == null ? GuiConfig.text("map-editor.games.ace-race.steps.progress-points.not-set") : vector.getBlockX() + ", " + vector.getBlockY() + ", " + vector.getBlockZ();
    }

    @Override
    public @NotNull Component listAddLabel() {
        return Component.text(GuiConfig.text("map-editor.games.ace-race.steps.progress-points.add-progress-points"));
    }

    @Override
    public @NotNull Component listAddHint() {
        return Component.text(GuiConfig.text("map-editor.games.ace-race.steps.progress-points.use-worldedit-to-select-the-progress-point-straight-line-and-click"));
    }

    private record Gate(@NotNull Vector pos1, @NotNull Vector pos2) {
    }
}

package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.api.game.acerace.AceRaceArea;
import ink.ziip.championshipscore.api.game.acerace.AceRaceConfig;
import ink.ziip.championshipscore.api.game.acerace.AceRaceEquipment;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AceRaceEquipmentGui;
import ink.ziip.championshipscore.api.game.area.prepare.gui.AnvilInputGui;
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
        super("progress_points", Component.text("竞速进度点"),
                Component.text("用 WorldEdit 选取水平直线；触发面从该高度向上延伸"),
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
        Utils.sendAdminInfo(player, "请输入该进度点之后的摔落高度；留空使用选线高度 " + defaultFallY + "。");
        AnvilInputGui.openInteger(player, "输入摔落高度", defaultFallY, fallY ->
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
                    Utils.sendAdminSuccess(player, "已添加进度点 #" + order + " &#696969• 摔落高度="
                            + fallY + " &#696969• 赛段道具=" + equipment.displayName());
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
            entries.add(new ListEntry("进度点 #" + progressPoint.getInt("order"), List.of(
                    "选线：" + format(progressPoint.getVector("pos1")) + " -> " + format(progressPoint.getVector("pos2")),
                    "触发高度：选线 Y 及以上",
                    "摔落高度：" + progressPoint.getInt("fall-y"),
                    "下一赛段道具：" + equipment.displayName())));
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
        Utils.sendAdminInfo(player, "请输入该进度点之后的摔落高度；留空保留当前值 " + defaultFallY + "。");
        AnvilInputGui.openInteger(player, "输入摔落高度", defaultFallY, fallY ->
                AceRaceEquipmentGui.open(player, session, current, equipment -> {
                    ConfigurationSection progressPoint = cfg(session.getTarget()).ensureProgressPoints()
                            .getConfigurationSection(key);
                    if (progressPoint == null) {
                        Utils.sendAdminError(player, "该进度点已不存在。");
                        return;
                    }
                    writeProgressPoint(progressPoint, order, gate, fallY, equipment);
                    session.markDirty();
                    reload(session);
                    Utils.sendAdminSuccess(player, "已更新进度点 #" + order + " &#696969• 摔落高度="
                            + fallY + " &#696969• 赛段道具=" + equipment.displayName());
                }));
        return null;
    }

    @Override
    public boolean listEditHandlesNavigation() {
        return true;
    }

    @Override
    public String listSetOrder(@NotNull PrepareSession session, @NotNull Player player,
                               int index, int newOrder) {
        ConfigurationSection root = cfg(session.getTarget()).ensureProgressPoints();
        List<String> keys = orderedKeys(root);
        if (index < 0 || index >= keys.size() || newOrder < 1 || newOrder > keys.size())
            return Utils.formatAdminError("序号必须在 1 到 " + keys.size() + " 之间。");
        String moved = keys.remove(index);
        keys.add(newOrder - 1, moved);
        renumber(root, keys);
        session.markDirty();
        reload(session);
        return Utils.formatAdminSuccess("已将进度点调整为第 " + newOrder + " 项。");
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
        return Utils.formatAdminSuccess("已删除第 " + (index + 1) + " 个进度点。");
    }

    private static Gate captureGate(@NotNull PrepareSession session, @NotNull Player player) {
        Vector[] selection;
        try {
            selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, true);
        } catch (Exception exception) {
            Utils.sendAdminError(player, "请先用 WorldEdit 选取进度点水平直线。");
            return null;
        }
        int spanX = Math.abs(selection[0].getBlockX() - selection[1].getBlockX());
        int spanY = Math.abs(selection[0].getBlockY() - selection[1].getBlockY());
        int spanZ = Math.abs(selection[0].getBlockZ() - selection[1].getBlockZ());
        if (spanY != 0 || (spanX > 0 && spanZ > 0) || (spanX == 0 && spanZ == 0)) {
            Utils.sendAdminError(player, "进度点必须是同一高度、沿 X 或 Z 方向延伸的 WorldEdit 直线。");
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

    private static void renumber(ConfigurationSection root, List<String> keys) {
        for (int i = 0; i < keys.size(); i++) root.getConfigurationSection(keys.get(i)).set("order", i + 1);
    }

    private static String format(Vector vector) {
        return vector == null ? "未设置" : vector.getBlockX() + ", " + vector.getBlockY() + ", " + vector.getBlockZ();
    }

    @Override
    public @NotNull Component listAddLabel() {
        return Component.text("添加进度点");
    }

    @Override
    public @NotNull Component listAddHint() {
        return Component.text("用 WorldEdit 选取进度点直线后点击");
    }

    private record Gate(@NotNull Vector pos1, @NotNull Vector pos2) {
    }
}

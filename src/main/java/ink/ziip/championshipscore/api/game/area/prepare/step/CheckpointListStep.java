package ink.ziip.championshipscore.api.game.area.prepare.step;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorConfig;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorTeamArea;
import ink.ziip.championshipscore.api.game.setup.SetupTarget;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Minimal guided checkpoint editor for Parkour Warrior. Each add creates a main checkpoint from the
 * current location and WorldEdit selection, while preserving the nested format consumed by the runtime.
 */
public final class CheckpointListStep extends PrepareStep {
    public CheckpointListStep() {
        super("checkpoints", Component.text("跑酷检查点"),
                Component.text("用当前位置和 WorldEdit 选区添加主检查点；至少需要一个"),
                Material.LIME_CONCRETE, StepCaptureType.LIST);
    }

    private static ParkourWarriorConfig cfg(SetupTarget target) {
        return (ParkourWarriorConfig) target.config();
    }

    private static void reload(PrepareSession session) {
        ParkourWarriorTeamArea area = session.getPlugin().getGameManager().getParkourWarriorManager()
                .getArea(session.getAreaName());
        if (area != null) area.loadCheckpoints();
    }

    @Override
    public boolean isSet(PrepareSession session) {
        return session != null && !cfg(session.getTarget()).ensureCheckpoints().getKeys(false).isEmpty();
    }

    @Override
    public boolean requiresWorldEdit() {
        return true;
    }

    @Override
    public String listAdd(@NotNull PrepareSession session, @NotNull Player player) {
        Vector[] selection;
        try {
            selection = session.getPlugin().getWorldEditManager().getPlayerSelection(player, false);
        } catch (Exception e) {
            return Utils.formatAdminError("请先用 WorldEdit 选取检查点进入区域。 ");
        }
        ParkourWarriorConfig config = cfg(session.getTarget());
        ConfigurationSection root = config.ensureCheckpoints();
        int index = root.getKeys(false).size() + 1;
        String key = "checkpoint" + index;
        while (root.isConfigurationSection(key)) key = "checkpoint" + (++index);
        ConfigurationSection checkpoint = root.createSection(key);
        checkpoint.set("name", key);
        checkpoint.set("type", "main");
        checkpoint.set("spawn", player.getLocation());
        checkpoint.set("enter.pos1", selection[0]);
        checkpoint.set("enter.pos2", selection[1]);
        checkpoint.createSection("sub-checkpoints");
        session.markDirty();
        reload(session);
        return Utils.formatAdminSuccess("已添加检查点 &#696969• 当前 &#fff566" + root.getKeys(false).size() + " &#ededed个");
    }

    @Override
    public String listClear(@NotNull PrepareSession session, @NotNull Player player) {
        ConfigurationSection root = cfg(session.getTarget()).ensureCheckpoints();
        for (String key : root.getKeys(false)) root.set(key, null);
        session.markDirty();
        reload(session);
        return Utils.formatAdminSuccess("已清空检查点。");
    }

    @Override
    public int listCount(@NotNull PrepareSession session) {
        return cfg(session.getTarget()).ensureCheckpoints().getKeys(false).size();
    }

    @Override
    public @NotNull Component listAddLabel() {
        return Component.text("添加主检查点");
    }

    @Override
    public @NotNull Component listAddHint() {
        return Component.text("站在出生点并选取进入区域后点击");
    }
}

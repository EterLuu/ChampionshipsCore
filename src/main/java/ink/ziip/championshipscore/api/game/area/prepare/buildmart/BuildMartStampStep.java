package ink.ziip.championshipscore.api.game.area.prepare.buildmart;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartLayout;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/** Builds the hub plus N team-base copies in the editable Build Mart world. */
final class BuildMartStampStep extends PrepareStep {
    private final File hub;
    private final File base;

    BuildMartStampStep(File hub, File base) {
        super("stamp", Component.text("生成大厅与队伍基地"),
                Component.text("输入队伍数，生成大厅和对应数量的基地"), Material.DISPENSER,
                StepCaptureType.STAMP);
        this.hub = hub;
        this.base = base;
    }

    @Override public boolean isSet(PrepareSession session) {
        return session != null && session.isStamped();
    }

    @Override public String stamp(@NotNull PrepareSession session, @NotNull Player player, int count) {
        if (count < 1) return Utils.formatAdminError("队伍数必须大于 0。");
        if (!hub.isFile() || !base.isFile())
            return Utils.formatAdminError("请先保存大厅模板和基地模板。");
        World world = Bukkit.getWorld(session.getTarget().worldName());
        if (world == null) return Utils.formatAdminError("地图世界尚未加载。");
        if (!session.getTarget().canSaveMap())
            return Utils.formatAdminError("同一地图仍有游戏实例运行，无法生成。");
        try {
            Vector hubSize = session.getPlugin().getWorldEditManager().getSchematicDimensions(hub);
            Vector baseSize = session.getPlugin().getWorldEditManager().getSchematicDimensions(base);
            BuildMartConfig config = (BuildMartConfig) session.getTarget().config();
            var previousGrid = config.getBaseGrid();
            Vector previousHubOrigin = config.getHubOrigin();
            Vector previousBaseOrigin = previousGrid.origin(0);
            ArenaPreparer.clearCopies(session.getPlugin(), world, previousGrid, config.getBaseCount(),
                    config.getBaseSchematicSize());
            if (config.getHubSchematicSize() != null)
                session.getPlugin().getWorldEditManager().clearCuboid(world, previousHubOrigin,
                        config.getHubSchematicSize());
            var grid = config.prepareBaseGrid(hubSize, baseSize);
            if (!previousBaseOrigin.equals(grid.origin(0))) config.invalidateMovedBaseGeometry();
            Vector hubOrigin = config.getHubOrigin();
            session.getPlugin().getWorldEditManager().pasteSchematic(world, hub,
                    hubOrigin.getBlockX(), hubOrigin.getBlockY(), hubOrigin.getBlockZ());
            ArenaPreparer.stampCopies(session.getPlugin(), world, base, grid, count);
        } catch (Exception e) {
            return Utils.formatAdminError("生成地图失败：&#fff566" + e.getMessage());
        }
        ((BuildMartConfig) session.getTarget().config()).setBaseCount(count);
        session.getTarget().config().markPrepareWorldBuilt();
        session.setWorldConfirmed(true);
        session.setStamped(true);
        player.teleportAsync(((BuildMartConfig) session.getTarget().config()).getBaseGrid().origin(0).toLocation(world));
        return Utils.formatAdminSuccess("已生成大厅和 &#fff566" + count + " &#ededed个基地；完成点位后请发布。");
    }
}

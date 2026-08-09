package ink.ziip.championshipscore.api.game.area.prepare.buildmart;

import ink.ziip.championshipscore.api.game.area.prepare.PrepareSession;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareStep;
import ink.ziip.championshipscore.api.game.area.prepare.StepCaptureType;
import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import ink.ziip.championshipscore.util.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/** Builds the 0th base template plus N playable team-base copies around the hand-built hub. */
final class BuildMartStampStep extends PrepareStep {
    private final File base;

    BuildMartStampStep(File base) {
        super("stamp", Component.text("生成队伍基地"),
                Component.text("输入可参赛队伍数；0 号只保留为模板，实际基地从 1 号开始"), Material.DISPENSER,
                StepCaptureType.STAMP);
        this.base = base;
    }

    @Override public boolean isSet(PrepareSession session) {
        return session != null && session.isStamped();
    }

    @Override public String stamp(@NotNull PrepareSession session, @NotNull Player player, int count) {
        if (count < 1) return Utils.formatAdminError("队伍数必须大于 0。");
        if (!base.isFile()) return Utils.formatAdminError("请先保存 0 号基地模板。");
        World world = Bukkit.getWorld(session.getTarget().worldName());
        if (world == null) return Utils.formatAdminError("地图世界尚未加载。");
        if (!session.getTarget().canSaveMap())
            return Utils.formatAdminError("同一地图仍有游戏实例运行，无法生成。");
        BuildMartConfig config = (BuildMartConfig) session.getTarget().config();
        if (config.getHubPos1() == null || config.getHubPos2() == null)
            return Utils.formatAdminError("请先设置资源大厅边界。");
        Vector baseOrigin = config.getBaseSourceOrigin();
        if (baseOrigin == null)
            return Utils.formatAdminError("请重新保存一次 0 号基地模板，以记录模板在世界中的位置。");
        try {
            Vector baseSize = session.getPlugin().getWorldEditManager().getSchematicDimensions(base);
            var previousGrid = config.getBaseGrid();
            Vector previousBaseSize = config.getBaseSchematicSize();
            // The persisted count excludes the 0th source template, so clear only physical copies 1..N.
            // Include one extra old ring index so maps stamped before copy 0 became the true centre do not
            // leave their final generated base behind after the layout is corrected.
            ArenaPreparer.clearAdditionalCopies(session.getPlugin(), world, previousGrid, config.getBaseCount() + 2,
                    previousBaseSize);
            var grid = config.prepareBaseGrid(baseOrigin, baseSize);
            // Index 0 remains the editable source template. Indices 1..N are the bases players actually use.
            ArenaPreparer.stampAdditionalCopies(session.getPlugin(), world, base, grid, count + 1);
        } catch (Exception e) {
            return Utils.formatAdminError("生成地图失败：&#fff566" + e.getMessage());
        }
        config.setBaseCount(count);
        session.getTarget().config().markPrepareWorldBuilt();
        session.setWorldConfirmed(true);
        session.setStamped(true);
        player.teleport(config.getBaseGrid().origin(0).toLocation(world));
        return Utils.formatAdminSuccess("已保留 0 号基地模板，并生成 &#fff566" + count
                + " &#ededed个实际队伍基地；完成点位后请发布。");
    }
}

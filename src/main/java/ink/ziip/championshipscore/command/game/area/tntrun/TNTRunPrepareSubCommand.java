package ink.ziip.championshipscore.command.game.area.tntrun;

import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunConfig;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunLayout;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunTeamArea;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Stamps out a TNT Run map from the {@code arena} schematic: pastes {@code copies} identical arenas in a row
 * (see {@link TNTRunLayout}), records the count, then persists the result into the static map template via
 * {@link TNTRunTeamArea#saveMap}. Players are spread across the copies at round start. Run once per area
 * (re-run to change the copy count); afterwards stand in copy 0 (pasted at {@link TNTRunLayout#FIRST}) and
 * set {@code copy-spawn}, plus an {@code area-pos} selection covering the whole stamped region.
 *
 * <p>Usage: {@code /cc game area tntrun prepare <area> <copies>}.
 */
public class TNTRunPrepareSubCommand extends BaseSubCommand {
    public TNTRunPrepareSubCommand() {
        super("prepare", "按赛道模板生成多份地图", "/cc game area tntrun prepare <场地> <份数>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 2) {
            sendUsage(sender);
            return true;
        }
        TNTRunTeamArea area = plugin.getGameManager().getTntRunManager().getArea(args[0]);
        if (area == null) {
            Utils.sendAdminError(sender, "找不到场地 #fff566" + args[0]);
            return true;
        }
        int copies;
        try {
            copies = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sendUsage(sender);
            return true;
        }
        if (copies < 1) {
            Utils.sendAdminError(sender, "赛道份数必须至少为 #fff5661");
            return true;
        }

        World world = Bukkit.getWorld(area.getWorldName());
        if (world == null) {
            Utils.sendAdminError(sender, "世界 #fff566" + area.getWorldName() + " #ededed尚未加载。");
            return true;
        }

        File file = new File(new File(new File(plugin.getDataFolder(), "tntrun"), "schematics"), "arena.schem");
        if (!file.isFile()) {
            Utils.sendAdminError(sender, "缺少赛道模板，请先执行 #fff566/cc game area tntrun schematic");
            return true;
        }

        Vector size;
        try {
            size = plugin.getWorldEditManager().getSchematicDimensions(file);
            ArenaPreparer.stampCopies(plugin, world, file, TNTRunLayout.GRID, copies);
        } catch (Exception e) {
            Utils.sendAdminError(sender, "生成赛道失败：#fff566" + e.getMessage());
            return true;
        }

        // Record copy count + size (per-copy boundaries are derived from these), then fix into the template.
        TNTRunConfig config = area.getGameConfig();
        config.setCopies(copies);
        config.setCopySize(size);
        config.saveOptions();
        area.saveMap(World.Environment.NORMAL);

        Utils.sendAdminSuccess(sender, "已生成并保存 #fff566" + copies + " #ededed份 TNT飞跃赛道，边界已计算。");
        Utils.sendAdminInfo(sender, "下一步：在 0 号赛道执行 #fff566/cc game area tntrun set "
                + args[0] + " copy-spawn");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getGameManager().getTntRunManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }
        return Collections.emptyList();
    }
}

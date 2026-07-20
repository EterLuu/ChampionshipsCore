package ink.ziip.championshipscore.command.game.area.tntrun;

import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunConfig;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunLayout;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunTeamArea;
import ink.ziip.championshipscore.command.BaseSubCommand;
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
            sender.sendMessage("§c找不到场地 §e" + args[0] + "§c。");
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
            sender.sendMessage("§c份数必须 ≥ 1。");
            return true;
        }

        World world = Bukkit.getWorld(area.getWorldName());
        if (world == null) {
            sender.sendMessage("§c世界 §e" + area.getWorldName() + " §c未加载，无法生成。");
            return true;
        }

        File file = new File(new File(new File(plugin.getDataFolder(), "tntrun"), "schematics"), "arena.schem");
        if (!file.isFile()) {
            sender.sendMessage("§c缺少 schematic：请先用 §e/cc game area tntrun schematic§c 保存赛道模板。");
            return true;
        }

        Vector size;
        try {
            size = plugin.getWorldEditManager().getSchematicDimensions(file);
            ArenaPreparer.stampCopies(plugin, world, file, TNTRunLayout.GRID, copies);
        } catch (Exception e) {
            sender.sendMessage("§c粘贴失败：" + e.getMessage());
            return true;
        }

        // Record copy count + size (per-copy boundaries are derived from these), then fix into the template.
        TNTRunConfig config = area.getGameConfig();
        config.setCopies(copies);
        config.setCopySize(size);
        config.saveOptions();
        area.saveMap(World.Environment.NORMAL);

        sender.sendMessage("§a已生成 §e" + copies + " §a份赛道并固化为模板（边界已自动计算）。");
        sender.sendMessage("§7进入世界、站到 0 号赛道(0,100,0 附近)用 §f/cc game area tntrun set "
                + args[0] + " copy-spawn §7设置出生点即可。");
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

package ink.ziip.championshipscore.command.game.area.parkourtag;

import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagArea;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagLayout;
import ink.ziip.championshipscore.command.BaseSubCommand;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Stamps out a Parkour Tag map from the {@code arena} schematic: pastes {@code copies} identical venues in a
 * row (see {@link ParkourTagLayout}) and persists into the static map template via {@link ParkourTagArea#saveMap}.
 * Each parallel match runs in one copy, so {@code copies} must be at least the number of concurrent matches
 * (teams / 2). Afterwards stand in copy 0 (pasted at {@link ParkourTagLayout#FIRST}) and set the template
 * points with {@code /cc game area parkourtag set <area> ...}.
 *
 * <p>Usage: {@code /cc game area parkourtag prepare <area> <copies>}.
 */
public class ParkourTagPrepareSubCommand extends BaseSubCommand {
    public ParkourTagPrepareSubCommand() {
        super("prepare", "按追逐场地模板生成多份地图", "/cc game area parkourtag prepare <场地> <份数>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 2) {
            sendUsage(sender);
            return true;
        }
        ParkourTagArea area = plugin.getGameManager().getParkourTagManager().getArea(args[0]);
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

        File file = new File(new File(new File(plugin.getDataFolder(), "parkourtag"), "schematics"), "arena.schem");
        if (!file.isFile()) {
            sender.sendMessage("§c缺少 schematic：请先用 §e/cc game area parkourtag schematic§c 保存追逐场地模板。");
            return true;
        }

        try {
            ArenaPreparer.stampCopies(plugin, world, file, ParkourTagLayout.GRID, copies);
        } catch (Exception e) {
            sender.sendMessage("§c粘贴失败：" + e.getMessage());
            return true;
        }

        area.saveMap(World.Environment.NORMAL);

        sender.sendMessage("§a已生成 §e" + copies + " §a份追逐场地并固化为模板。");
        sender.sendMessage("§7进入世界、站到 0 号场地(0,100,0 附近)用 §f/cc game area parkourtag set "
                + args[0] + " <点位> §7配置预备点、两个笼子的追逐者/逃生者出生点与区域。");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getGameManager().getParkourTagManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }
        return Collections.emptyList();
    }
}

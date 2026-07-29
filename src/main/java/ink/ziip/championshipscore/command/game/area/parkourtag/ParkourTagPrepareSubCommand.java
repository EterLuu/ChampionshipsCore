package ink.ziip.championshipscore.command.game.area.parkourtag;

import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagArea;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagLayout;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
        super("prepare", "追逐场地准备 GUI（无参）；或 <场地> <份数> 直接盖章", "/cc game area parkourtag prepare [场地 份数]");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                Utils.sendAdminError(sender, "准备向导只能由玩家打开。");
                return true;
            }
            plugin.getPrepareSessionManager().openAreaListGui(player, GameTypeEnum.ParkourTag);
            return true;
        }
        if (args.length != 2) {
            sendUsage(sender);
            return true;
        }
        ParkourTagArea area = plugin.getGameManager().getParkourTagManager().getArea(args[0]);
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
            Utils.sendAdminError(sender, "场地份数必须至少为 #fff5661");
            return true;
        }

        World world = Bukkit.getWorld(area.getWorldName());
        if (world == null) {
            Utils.sendAdminError(sender, "世界 #fff566" + area.getWorldName() + " #ededed尚未加载。");
            return true;
        }

        File file = new File(new File(new File(plugin.getDataFolder(), "parkourtag"), "schematics"), "arena.schem");
        if (!file.isFile()) {
            Utils.sendAdminError(sender, "缺少场地模板，请先执行 #fff566/cc game area parkourtag schematic");
            return true;
        }

        try {
            ArenaPreparer.stampCopies(plugin, world, file, ParkourTagLayout.GRID, copies);
        } catch (Exception e) {
            Utils.sendAdminError(sender, "生成场地失败：#fff566" + e.getMessage());
            return true;
        }

        area.saveMap(World.Environment.NORMAL);

        Utils.sendAdminSuccess(sender, "已生成并保存 #fff566" + copies + " #ededed份跑酷追击场地。");
        Utils.sendAdminInfo(sender, "下一步：在 0 号场地执行 #fff566/cc game area parkourtag set "
                + args[0] + " <点位>");
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

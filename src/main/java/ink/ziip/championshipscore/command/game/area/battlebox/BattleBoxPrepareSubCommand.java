package ink.ziip.championshipscore.command.game.area.battlebox;

import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxLayout;
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
 * Stamps out a Battle Box map from the {@code arena} schematic: pastes {@code copies} identical arenas in a
 * row (see {@link BattleBoxLayout}) and persists into the static map template via {@link BattleBoxArea#saveMap}.
 * Each parallel match runs in one copy, so {@code copies} must be at least the number of concurrent matches
 * (teams / 2). After this, stand in copy 0 (pasted at {@link BattleBoxLayout#FIRST}) and set the template
 * points with {@code /cc game area battlebox set <area> ...}.
 *
 * <p>Usage: {@code /cc game area battlebox prepare <area> <copies>}.
 */
public class BattleBoxPrepareSubCommand extends BaseSubCommand {
    public BattleBoxPrepareSubCommand() {
        super("prepare", "按对战场地模板生成多份地图", "/cc game area battlebox prepare <场地> <份数>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 2) {
            sendUsage(sender);
            return true;
        }
        BattleBoxArea area = plugin.getGameManager().getBattleBoxManager().getArea(args[0]);
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

        File file = new File(new File(new File(plugin.getDataFolder(), "battlebox"), "schematics"), "arena.schem");
        if (!file.isFile()) {
            sender.sendMessage("§c缺少 schematic：请先用 §e/cc game area battlebox schematic§c 保存对战场地模板。");
            return true;
        }

        try {
            ArenaPreparer.stampCopies(plugin, world, file, BattleBoxLayout.GRID, copies);
        } catch (Exception e) {
            sender.sendMessage("§c粘贴失败：" + e.getMessage());
            return true;
        }

        area.saveMap(World.Environment.NORMAL);

        sender.sendMessage("§a已生成 §e" + copies + " §a份对战场地并固化为模板。");
        sender.sendMessage("§7进入世界、站到 0 号场地(0,100,0 附近)用 §f/cc game area battlebox set "
                + args[0] + " <点位> §7配置左右出生/预备点、wool-pos、area-pos、potion 点。");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getGameManager().getBattleBoxManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }
        return Collections.emptyList();
    }
}

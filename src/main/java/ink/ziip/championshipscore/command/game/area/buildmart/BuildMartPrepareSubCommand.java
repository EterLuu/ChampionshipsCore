package ink.ziip.championshipscore.command.game.area.buildmart;

import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartArea;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartLayout;
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
 * Stamps out a full Build Mart map from the {@code hub} and {@code base} schematics: pastes the hub at
 * {@link BuildMartLayout#HUB} and one base copy at every seat's grid origin, then persists the result back
 * into the static map template via {@link BuildMartArea#saveMap}. After this, every round just loads the
 * pre-built world — no runtime cloning. Run it once per area (re-run to change team count / rebuild).
 *
 * <p>Usage: {@code /cc game area buildmart prepare <area> <teams>}.
 */
public class BuildMartPrepareSubCommand extends BaseSubCommand {
    public BuildMartPrepareSubCommand() {
        super("prepare", "按大厅+基地模板生成地图", "/cc game area buildmart prepare <场地> <队伍数>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 2) {
            sendUsage(sender);
            return true;
        }
        BuildMartArea area = plugin.getGameManager().getBuildMartManager().getArea(args[0]);
        if (area == null) {
            sender.sendMessage("§c找不到场地 §e" + args[0] + "§c。");
            return true;
        }
        int teams;
        try {
            teams = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sendUsage(sender);
            return true;
        }
        if (teams < 1) {
            sender.sendMessage("§c队伍数必须 ≥ 1。");
            return true;
        }

        World world = Bukkit.getWorld(area.getWorldName());
        if (world == null) {
            sender.sendMessage("§c世界 §e" + area.getWorldName() + " §c未加载，无法生成。");
            return true;
        }

        File schematics = new File(new File(plugin.getDataFolder(), "buildmart"), "schematics");
        File hubFile = new File(schematics, "hub.schem");
        File baseFile = new File(schematics, "base.schem");
        if (!hubFile.isFile() || !baseFile.isFile()) {
            sender.sendMessage("§c缺少 schematic：请先用 §e/cc game area buildmart schematic hub§c 和 §ebase§c 保存模板。");
            return true;
        }

        try {
            plugin.getWorldEditManager().pasteSchematic(world, hubFile,
                    BuildMartLayout.HUB.getBlockX(), BuildMartLayout.HUB.getBlockY(), BuildMartLayout.HUB.getBlockZ());
            ArenaPreparer.stampCopies(plugin, world, baseFile, BuildMartLayout.GRID, teams);
        } catch (Exception e) {
            sender.sendMessage("§c粘贴失败：" + e.getMessage());
            return true;
        }

        // Persist the freshly stamped world back into the static template so every round loads it.
        area.saveMap(World.Environment.NORMAL);

        sender.sendMessage("§a已生成地图：大厅 + §e" + teams + " §a个基地，并固化为模板。");
        sender.sendMessage("§7现在进入世界、站到 0 号基地(0,100,500)处用 §f/cc game area buildmart set "
                + args[0] + " base <键> §7配置基地锚点。");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getGameManager().getBuildMartManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }
        return Collections.emptyList();
    }
}

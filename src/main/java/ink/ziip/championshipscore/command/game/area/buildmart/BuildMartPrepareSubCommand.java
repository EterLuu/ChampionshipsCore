package ink.ziip.championshipscore.command.game.area.buildmart;

import ink.ziip.championshipscore.api.game.arena.ArenaPreparer;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartArea;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartLayout;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
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
            Utils.sendAdminError(sender, "找不到场地 &#fff566" + args[0]);
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
            Utils.sendAdminError(sender, "队伍数必须至少为 &#fff5661");
            return true;
        }

        World world = Bukkit.getWorld(area.getWorldName());
        if (world == null) {
            Utils.sendAdminError(sender, "世界 &#fff566" + area.getWorldName() + " &#ededed尚未加载。");
            return true;
        }
        if (!area.canSaveMap()) {
            Utils.sendAdminError(sender, "同一地图仍有游戏实例运行，无法重新生成或保存。");
            return true;
        }

        File schematics = new File(new File(plugin.getDataFolder(), "buildmart"), "schematics");
        File hubFile = new File(schematics, "hub.schem");
        File baseFile = new File(schematics, "base.schem");
        if (!hubFile.isFile() || !baseFile.isFile()) {
            Utils.sendAdminError(sender, "缺少模板，请先分别保存 &#fff566hub &#ededed和 &#fff566base");
            return true;
        }

        try {
            plugin.getWorldEditManager().pasteSchematic(world, hubFile,
                    BuildMartLayout.HUB.getBlockX(), BuildMartLayout.HUB.getBlockY(), BuildMartLayout.HUB.getBlockZ());
            ArenaPreparer.stampCopies(plugin, world, baseFile, BuildMartLayout.GRID, teams);
        } catch (Exception e) {
            Utils.sendAdminError(sender, "生成地图失败：&#fff566" + e.getMessage());
            return true;
        }

        // Persist the freshly stamped world back into the static template so every round loads it.
        if (!area.saveMap(World.Environment.NORMAL)) {
            Utils.sendAdminError(sender, "地图保存失败，请查看控制台日志；基地数量未写入配置。");
            return true;
        }
        area.getGameConfig().setBaseCount(teams);
        area.getGameConfig().saveOptions();

        Utils.sendAdminSuccess(sender, "已生成并保存建材集市地图：大厅 + &#fff566" + teams + " &#ededed个基地。");
        Utils.sendAdminInfo(sender, "下一步：在 0 号基地执行 &#fff566/cc game area buildmart set "
                + args[0] + " base <键>");
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

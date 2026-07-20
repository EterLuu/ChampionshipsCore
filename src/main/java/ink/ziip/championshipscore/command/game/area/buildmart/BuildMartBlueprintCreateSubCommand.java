package ink.ziip.championshipscore.command.game.area.buildmart;

import ink.ziip.championshipscore.command.BaseSubCommand;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exports the player's WorldEdit selection into a Build Mart blueprint yml. Block offsets are stored
 * relative to the selection's minimum corner so the build anchor maps to that corner in-game. Air is
 * skipped. After writing, the shared blueprint pool is reloaded so the order is immediately drawable.
 *
 * <p>Usage: {@code /cc game area buildmart blueprint create <name> <stars>}.
 */
public class BuildMartBlueprintCreateSubCommand extends BaseSubCommand {
    /** Safety cap on exported block count to avoid accidentally serializing a giant region. */
    private static final int MAX_BLOCKS = 20000;

    public BuildMartBlueprintCreateSubCommand() {
        super("create", "从WE选区导出蓝图", "/cc game area buildmart blueprint create <名称> <星级>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 2 || !(sender instanceof Player player)) {
            sendUsage(sender);
            return true;
        }
        String name = args[0].toLowerCase();
        int stars;
        try {
            stars = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sendUsage(sender);
            return true;
        }

        Vector[] selection;
        try {
            selection = plugin.getWorldEditManager().getPlayerSelection(player, true);
        } catch (Exception e) {
            sender.sendMessage("§c无法读取 WorldEdit 选区，请先用 //pos1 //pos2 选择区域。");
            return true;
        }
        Vector min = Vector.getMinimum(selection[0], selection[1]);
        Vector max = Vector.getMaximum(selection[0], selection[1]);
        World world = player.getWorld();

        List<String> blocks = new ArrayList<>();
        for (int x = min.getBlockX(); x <= max.getBlockX(); x++) {
            for (int y = min.getBlockY(); y <= max.getBlockY(); y++) {
                for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) continue;
                    int ox = x - min.getBlockX();
                    int oy = y - min.getBlockY();
                    int oz = z - min.getBlockZ();
                    blocks.add(ox + "," + oy + "," + oz + "=" + block.getBlockData().getAsString());
                    if (blocks.size() > MAX_BLOCKS) {
                        sender.sendMessage("§c选区方块数超过上限 " + MAX_BLOCKS + "，已取消。");
                        return true;
                    }
                }
            }
        }
        if (blocks.isEmpty()) {
            sender.sendMessage("§c选区内没有任何非空气方块。");
            return true;
        }

        File dir = new File(new File(plugin.getDataFolder(), "buildmart"), "blueprints");
        dir.mkdirs();
        File file = new File(dir, name + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", name);
        yaml.set("stars", stars);
        yaml.set("blocks", blocks);
        try {
            yaml.save(file);
        } catch (Exception e) {
            sender.sendMessage("§c保存蓝图失败：" + e.getMessage());
            return true;
        }

        plugin.getGameManager().getBuildMartManager().reloadOrderPool();
        sender.sendMessage("§a已导出蓝图 §e" + name + " §7(" + stars + "★, " + blocks.size() + " 个方块)，蓝图库已刷新。");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

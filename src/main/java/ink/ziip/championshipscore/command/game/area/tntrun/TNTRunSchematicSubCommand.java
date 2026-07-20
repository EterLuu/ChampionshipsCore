package ink.ziip.championshipscore.command.game.area.tntrun;

import ink.ziip.championshipscore.command.BaseSubCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Saves the player's current WorldEdit selection as the single TNT Run arena template used by
 * {@code prepare}, to {@code plugin/tntrun/schematics/arena.schem}. {@code prepare} then stamps this out N
 * times in a row so players can be split across identical copies.
 *
 * <p>Usage: {@code /cc game area tntrun schematic}.
 */
public class TNTRunSchematicSubCommand extends BaseSubCommand {
    public TNTRunSchematicSubCommand() {
        super("schematic", "把WE选区保存为赛道模板", "/cc game area tntrun schematic");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sendUsage(sender);
            return true;
        }
        File dir = new File(new File(plugin.getDataFolder(), "tntrun"), "schematics");
        File file = new File(dir, "arena.schem");
        try {
            plugin.getWorldEditManager().saveSelectionAsSchematic(player, file);
        } catch (Exception e) {
            sender.sendMessage("§c保存失败，请先用 //pos1 //pos2 选好赛道：" + e.getMessage());
            return true;
        }
        sender.sendMessage("§a已保存赛道模板到 §7" + file.getName() + "§a，可用于 prepare。");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

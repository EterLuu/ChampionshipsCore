package ink.ziip.championshipscore.command.game.area.battlebox;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Saves the player's current WorldEdit selection as the single Battle Box arena template (one full
 * two-team box) used by {@code prepare}, to {@code plugin/battlebox/schematics/arena.schem}. {@code prepare}
 * stamps it out N times so N team-vs-team matches can run in parallel.
 *
 * <p>Usage: {@code /cc game area battlebox schematic}.
 */
public class BattleBoxSchematicSubCommand extends BaseSubCommand {
    public BattleBoxSchematicSubCommand() {
        super("schematic", "把WE选区保存为对战场地模板", "/cc game area battlebox schematic");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sendUsage(sender);
            return true;
        }
        File dir = new File(new File(plugin.getDataFolder(), "battlebox"), "schematics");
        File file = new File(dir, "arena.schem");
        try {
            plugin.getWorldEditManager().saveSelectionAsSchematic(player, file);
        } catch (Exception e) {
            Utils.sendAdminError(sender, "保存模板失败，请检查 WorldEdit 选区：&#fff566" + e.getMessage());
            return true;
        }
        Utils.sendAdminSuccess(sender, "已保存斗战方框模板：&#fff566" + file.getName());
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

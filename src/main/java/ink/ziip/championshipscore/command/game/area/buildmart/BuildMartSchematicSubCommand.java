package ink.ziip.championshipscore.command.game.area.buildmart;

import ink.ziip.championshipscore.command.BaseSubCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Saves the player's current WorldEdit selection as one of the Build Mart building blocks used by
 * {@code prepare}: {@code hub} or {@code base}. Files land in {@code plugin/buildmart/schematics/<name>.schem}
 * with their origin pinned to the selection's minimum corner, so {@code prepare} can paste them at exact
 * grid coordinates.
 *
 * <p>Usage: {@code /cc game area buildmart schematic <hub|base>}.
 */
public class BuildMartSchematicSubCommand extends BaseSubCommand {
    private final String[] names = {"hub", "base"};

    public BuildMartSchematicSubCommand() {
        super("schematic", "把WE选区保存为大厅/基地模板", "/cc game area buildmart schematic <hub|base>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            sendUsage(sender);
            return true;
        }
        String name = args[0].toLowerCase();
        if (Arrays.stream(names).noneMatch(n -> n.equals(name))) {
            sendUsage(sender);
            return true;
        }

        File dir = new File(new File(plugin.getDataFolder(), "buildmart"), "schematics");
        File file = new File(dir, name + ".schem");
        try {
            plugin.getWorldEditManager().saveSelectionAsSchematic(player, file);
        } catch (Exception e) {
            sender.sendMessage("§c保存失败，请先用 //pos1 //pos2 选好区域：" + e.getMessage());
            return true;
        }
        sender.sendMessage("§a已保存 §e" + name + " §a模板到 §7" + file.getName() + "§a，可用于 prepare。");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = new ArrayList<>(Arrays.asList(names));
            returnList.removeIf(s -> !s.startsWith(args[0].toLowerCase()));
            return returnList;
        }
        return Collections.emptyList();
    }
}

package ink.ziip.championshipscore.command.game.area.buildmart;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
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
            Utils.sendAdminError(sender, "保存模板失败，请检查 WorldEdit 选区：&#fff566" + e.getMessage());
            return true;
        }
        Utils.sendAdminSuccess(sender, "已保存建材集市 &#fff566" + name + " &#ededed模板：&#fff566" + file.getName());
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

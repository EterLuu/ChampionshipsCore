package ink.ziip.championshipscore.command.game.area.decarnival;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class DragonEggCarnivalAreaSaveSubCommand extends BaseSubCommand {
    public DragonEggCarnivalAreaSaveSubCommand() {
        super("save");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (plugin.getGameManager().getDragonEggCarnivalManager().saveArea(args[0])) {
                String message = MessageConfig.AREA_SUCCESSFULLY_SAVED
                        .replace("%area%", args[0]);
                sender.sendMessage(message);
                return true;
            }
            String message = MessageConfig.AREA_SAVED_FAILED
                    .replace("%area%", args[0])
                    .replace("%reason%", MessageConfig.REASON_AREA_NOT_IN_WAITING_STATUS);
            sender.sendMessage(message);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getGameManager().getDragonEggCarnivalManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }

        return Collections.emptyList();
    }
}

package ink.ziip.championshipscore.command.game.area.tgttos;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class TGTTOSAreaAddSubCommand extends BaseSubCommand {
    public TGTTOSAreaAddSubCommand() {
        super("add");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (plugin.getGameManager().getTgttosManager().addArea(args[0])) {
                String message = MessageConfig.AREA_SUCCESSFULLY_ADDED
                        .replace("%area%", args[0]);
                sender.sendMessage(message);
                return true;
            }
            String message = MessageConfig.AREA_ADDED_FAILED
                    .replace("%area%", args[0])
                    .replace("%reason%", MessageConfig.REASON_AREA_ALREADY_EXIST);
            sender.sendMessage(message);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

package ink.ziip.championshipscore.command.game.area.buildmart;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class BuildMartAreaAddSubCommand extends BaseSubCommand {
    public BuildMartAreaAddSubCommand() {
        super("add", "新建建材集市场地", "/cc game area buildmart add <场地名>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }
        if (plugin.getGameManager().getBuildMartManager().addArea(args[0])) {
            sender.sendMessage(MessageConfig.AREA_SUCCESSFULLY_ADDED.replace("%area%", args[0]));
            return true;
        }
        sender.sendMessage(MessageConfig.AREA_ADDED_FAILED
                .replace("%area%", args[0])
                .replace("%reason%", MessageConfig.REASON_AREA_ALREADY_EXIST));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

package ink.ziip.championshipscore.command.game.start.buildmart;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class BuildMartStartAllSubCommand extends BaseSubCommand {
    public BuildMartStartAllSubCommand() {
        super("all", "所有队伍开始建材集市", "/cc game start buildmart all <场地>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }
        String message = MessageConfig.GAME_SINGLE_GAME_START_FAILED;

        if (plugin.getGameManager().joinSingleTeamAreaForAllTeams(GameTypeEnum.BuildMart, args[0]))
            message = MessageConfig.GAME_SINGLE_GAME_START_SUCCESSFUL;

        message = message
                .replace("%game%", GameTypeEnum.BuildMart.toString())
                .replace("%area%", args[0]);

        sender.sendMessage(message);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getGameManager().getBuildMartManager().getAreaNameList();
            return filterStartsWith(returnList, args[0]);
        }

        return Collections.emptyList();
    }
}

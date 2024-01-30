package ink.ziip.championshipscore.command.game.start.snowball;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class SnowBallStartAllSubCommand extends BaseSubCommand {
    public SnowBallStartAllSubCommand() {
        super("all");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String message = MessageConfig.GAME_SINGLE_GAME_START_FAILED;

            if (plugin.getGameManager().joinSingleTeamAreaForAllTeams(GameTypeEnum.SnowballShowdown, args[0]))
                message = MessageConfig.GAME_SINGLE_GAME_START_SUCCESSFUL;

            message = message
                    .replace("%game%", GameTypeEnum.SnowballShowdown.toString())
                    .replace("%area%", args[0]);

            sender.sendMessage(message);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getGameManager().getSnowballShowdownManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }

        return Collections.emptyList();
    }
}

package ink.ziip.championshipscore.command.game.start.bingo;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class BingoStartAllSubCommand extends BaseSubCommand {
    public BingoStartAllSubCommand() {
        super("all", "所有队伍开始宾果飞速", "/cc game start bingo all <场地>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }
        plugin.getGameManager().joinSingleTeamAreaForAllTeamsAsync(
                GameTypeEnum.Bingo, args[0], false,
                ink.ziip.championshipscore.api.object.game.GameRunMode.GAME).thenAccept(started -> {
            String message = (started ? MessageConfig.GAME_SINGLE_GAME_START_SUCCESSFUL
                    : MessageConfig.GAME_SINGLE_GAME_START_FAILED)
                    .replace("%game%", GameTypeEnum.Bingo.toString())
                    .replace("%area%", args[0]);
            sender.sendMessage(message);
        });
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getGameManager().getBingoManager().getAreaNameList();
            return filterStartsWith(returnList, args[0]);
        }

        return Collections.emptyList();
    }
}

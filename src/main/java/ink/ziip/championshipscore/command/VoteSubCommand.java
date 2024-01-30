package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class VoteSubCommand extends BaseSubCommand {
    public VoteSubCommand() {
        super("vote");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            GameTypeEnum gameTypeEnum = null;
            try {
                gameTypeEnum = GameTypeEnum.valueOf(args[0]);
            } catch (Exception ignored) {
                sender.sendMessage(MessageConfig.VOTE_VOTE_FAILED_NOT_GAME);
                return true;
            }

            if (gameTypeEnum == GameTypeEnum.DragonEggCarnival) {
                sender.sendMessage(MessageConfig.VOTE_VOTE_FAILED_NOT_GAME);
                return true;
            }

            plugin.getVoteManager().vote((Player) sender, gameTypeEnum);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> returnList = new ArrayList<>();
        for (GameTypeEnum gameTypeEnum : GameTypeEnum.values()) {
            returnList.add(gameTypeEnum.name());
        }
        return returnList;
    }
}

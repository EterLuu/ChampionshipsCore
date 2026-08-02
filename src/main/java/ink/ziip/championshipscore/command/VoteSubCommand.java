package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class VoteSubCommand extends BaseSubCommand {
    public VoteSubCommand() {
        super("vote", "为下一个游戏投票", "/cc vote <游戏>", PLAYER_PERMISSION);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Utils.sendAdminError(sender, "该命令只能由玩家执行");
            return true;
        }
        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }
        if (args.length == 1) {
            GameTypeEnum gameTypeEnum = null;
            for (GameTypeEnum candidate : GameTypeEnum.values()) {
                if (candidate.name().equalsIgnoreCase(args[0])) {
                    gameTypeEnum = candidate;
                    break;
                }
            }
            if (gameTypeEnum == null) {
                sender.sendMessage(MessageConfig.VOTE_VOTE_FAILED_NOT_GAME);
                return true;
            }

            if (gameTypeEnum == GameTypeEnum.DragonEggCarnival || gameTypeEnum == GameTypeEnum.Dodgebolt
                    || !plugin.getGameManager().isGameEnabled(gameTypeEnum)) {
                sender.sendMessage(MessageConfig.VOTE_VOTE_FAILED_NOT_GAME);
                return true;
            }

            plugin.getVoteManager().vote(player, gameTypeEnum);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> returnList = new ArrayList<>();
        if (args.length == 1) {
            for (GameTypeEnum gameTypeEnum : GameTypeEnum.values()) {
                if (plugin.getGameManager().isGameEnabled(gameTypeEnum)
                        && gameTypeEnum != GameTypeEnum.DragonEggCarnival
                        && gameTypeEnum != GameTypeEnum.Dodgebolt) {
                    returnList.add(gameTypeEnum.name());
                }
            }
        }
        return filterStartsWith(returnList, args.length == 1 ? args[0] : "");
    }
}

package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class VoteSubCommand extends BaseSubCommand {
    public VoteSubCommand() {
        super("vote", "打开投票菜单或直接投票", "/cc vote [游戏]", PLAYER_PERMISSION);
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            CoreMessages.sendAdminError(sender, MessageConfig.COMMAND_PLAYER_ONLY);
            return true;
        }
        if (args.length > 1) {
            sendUsage(sender);
            return true;
        }
        if (args.length == 0) {
            plugin.getVoteManager().openVoteMenu(player);
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

            if (player.hasPermission(MainCommand.ADMIN_PERMISSION)) {
                player.sendMessage(MessageConfig.VOTE_VOTE_FAILED_ADMIN);
                return true;
            }
            plugin.getVoteManager().vote(player, gameTypeEnum);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        List<String> returnList = new ArrayList<>();
        if (args.length == 1) {
            for (GameTypeEnum gameTypeEnum : GameTypeEnum.values()) {
                if (plugin.getVoteManager().canVoteFor(gameTypeEnum)) {
                    returnList.add(gameTypeEnum.name());
                }
            }
        }
        return filterStartsWith(returnList, args.length == 1 ? args[0] : "");
    }
}

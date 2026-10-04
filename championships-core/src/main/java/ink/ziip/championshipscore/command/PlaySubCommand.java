package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PlaySubCommand extends BaseSubCommand {
    public PlaySubCommand() {
        super("play", "自由游玩与排行榜", "/cc play [leave|leaderboard]", PLAYER_PERMISSION);
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
        if (args.length == 0) {
            plugin.getDailyManager().openMenu(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("leave")) {
            if (!plugin.getDailyManager().leavePlay(player.getUniqueId()))
                player.sendMessage(
                        ink.ziip.championshipscore.platform.bukkit.text.LegacyText
                                .translateColorCodes(
                                        CoreMessages.dailyMessage(
                                                ink.ziip.championshipscore.configuration.config
                                                        .message.MessageConfig.DAILY_NOT_IN_PLAY)));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("leaderboard")) {
            plugin.getDailyManager().openLeaderboard(player);
            return true;
        }
        sendUsage(sender);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        return args.length == 1 ? complete(List.of("leave", "leaderboard"), args[0]) : List.of();
    }
}

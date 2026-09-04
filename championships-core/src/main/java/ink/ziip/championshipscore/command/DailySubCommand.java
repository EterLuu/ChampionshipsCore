package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.api.daily.DailyStatSnapshot;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DailySubCommand extends BaseSubCommand {
    public DailySubCommand() {
        super("daily", "自由游玩匹配与个人统计", "/cc daily <leave|stats> [游戏]", PLAYER_PERMISSION);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Utils.sendAdminError(sender, MessageConfig.COMMAND_PLAYER_ONLY);
            return true;
        }
        if (args.length < 1) { sendUsage(sender); return true; }
        if (args[0].equalsIgnoreCase("leave")) {
            if (!plugin.getDailyManager().leavePlay(player.getUniqueId()))
                message(sender, ink.ziip.championshipscore.configuration.config.message.MessageConfig.DAILY_NOT_IN_PLAY);
            return true;
        }
        if (args[0].equalsIgnoreCase("stats")) {
            GameTypeEnum game = args.length > 1 ? parseGame(args[1]) : null;
            if (args.length > 1 && game == null) { message(sender, MessageConfig.COMMAND_UNKNOWN_GAME); return true; }
            DailyStatSnapshot stat = plugin.getDailyManager().statsManager().stat(player.getUniqueId(), game);
            message(sender, MessageConfig.COMMAND_DAILY_STATS
                    .replace("%game%", game == null ? MessageConfig.DAILY_MODE_CHAMPIONSHIP : game.toString())
                    .replace("%games%", String.valueOf(stat.gamesPlayed())));
            return true;
        }
        sendUsage(sender);
        return true;
    }

    private GameTypeEnum parseGame(String value) {
        GameTypeEnum game = GameTypeEnum.fromCommand(value);
        return game != null && plugin.getDailyManager().enabledGames().contains(game) ? game : null;
    }

    private void message(CommandSender sender, String value) {
        sender.sendMessage(Utils.translateColorCodes(
                ink.ziip.championshipscore.configuration.config.message.MessageConfig.DAILY_PREFIXED.replace("%message%", value)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return complete(List.of("leave", "stats"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("stats"))
            return complete(plugin.getDailyManager().enabledGames().stream().map(GameTypeEnum::commandName).toList(), args[1]);
        return List.of();
    }
}

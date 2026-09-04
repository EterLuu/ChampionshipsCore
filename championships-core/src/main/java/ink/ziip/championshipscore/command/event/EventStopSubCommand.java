package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class EventStopSubCommand extends BaseSubCommand {
    public EventStopSubCommand() {
        super("stop", "停止正式比赛的赛程任务", "/cc event stop <游戏>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }
        GameTypeEnum game = EventCommandSupport.parse(args[0]);
        if (game == null || !plugin.getGameManager().isGameEnabled(game) || !EventCommandSupport.canSchedule(game)) {
            Utils.sendAdminError(sender, MessageConfig.EVENT_STOP_UNAVAILABLE);
            return true;
        }
        if (plugin.getScheduleManager().stopFormalEvent(game))
            Utils.sendAdminSuccess(sender, MessageConfig.EVENT_STOPPED.replace("%game%", game.name()));
        else
            Utils.sendAdminInfo(sender, MessageConfig.EVENT_NOT_RUNNING.replace("%game%", game.name()));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1)
            return filterStartsWith(EventCommandSupport.enabledFormalGames(), args[0]);
        return Collections.emptyList();
    }
}

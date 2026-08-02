package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
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
            Utils.sendAdminError(sender, "该游戏没有可停止的正式赛程");
            return true;
        }
        if (plugin.getScheduleManager().stopFormalEvent(game))
            Utils.sendAdminSuccess(sender, "已停止正式比赛赛程：&#fff566" + game);
        else
            Utils.sendAdminInfo(sender, "该正式比赛当前未运行：&#fff566" + game);
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

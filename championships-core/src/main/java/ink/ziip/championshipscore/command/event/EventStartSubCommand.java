package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.ScheduleManager;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class EventStartSubCommand extends BaseSubCommand {
    public EventStartSubCommand() {
        super("start", "开始正式比赛；进行中时再次执行会紧急停止",
                "/cc event start <游戏> [队伍1 队伍2] [--force]");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }
        GameTypeEnum game = EventCommandSupport.parse(args[0]);
        if (game == null || !plugin.getGameManager().isGameEnabled(game) || !EventCommandSupport.canSchedule(game)) {
            Utils.sendAdminError(sender, "该游戏不能作为当前正式比赛启动");
            return true;
        }

        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }

        ScheduleManager.EventAction action = plugin.getScheduleManager().startOrStopFormalEvent(game);
        if (action == ScheduleManager.EventAction.STARTED) {
            Utils.sendAdminSuccess(sender, "正式比赛已开始准备：&#fff566" + game);
        } else if (action == ScheduleManager.EventAction.STOPPED) {
            Utils.sendAdminInfo(sender, "已通过重复 start 紧急停止正式比赛：&#fff566" + game);
        } else if (action == ScheduleManager.EventAction.UNAVAILABLE) {
            Utils.sendAdminError(sender, "Bingo 执行端尚未就绪、已有比赛运行，或参赛者当前不可用");
        } else {
            Utils.sendAdminError(sender, "该游戏没有可用的正式比赛赛程");
        }
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

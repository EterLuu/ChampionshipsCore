package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.event.EventStateStore;
import ink.ziip.championshipscore.api.finale.FinaleGameRegistry;
import ink.ziip.championshipscore.api.schedule.ScheduleManager;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
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
                "/cc event start <游戏>");
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
            Utils.sendAdminError(sender, MessageConfig.EVENT_START_GAME_INVALID);
            return true;
        }
        EventStateStore.ActiveEvent active = new EventStateStore(plugin).load();
        // Imports are optional for every game; imported events keep their configured restrictions.
        if (active != null) {
            if (active.archived()) {
                Utils.sendAdminError(sender, MessageConfig.EVENT_START_ARCHIVED);
                return true;
            }
            if (!active.allows(game)) {
                Utils.sendAdminError(sender, MessageConfig.EVENT_START_GAME_NOT_IN_EVENT
                        .replace("%event%", active.title()).replace("%game%", game.name()));
                return true;
            }
        }

        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }

        if (FinaleGameRegistry.isRegistered(game)) {
            plugin.getScheduleManager().requestFinale(game, null, null, null, sender, false);
            return true;
        }

        ScheduleManager.EventAction action = plugin.getScheduleManager().startOrStopFormalEvent(game);
        if (action == ScheduleManager.EventAction.STARTED) {
            Utils.sendAdminSuccess(sender, MessageConfig.EVENT_START_STARTED
                    .replace("%game%", game.name()));
        } else if (action == ScheduleManager.EventAction.STOPPED) {
            Utils.sendAdminInfo(sender, MessageConfig.EVENT_START_EMERGENCY_STOPPED
                    .replace("%game%", game.name()));
        } else if (action == ScheduleManager.EventAction.UNAVAILABLE) {
            String reason = plugin.getScheduleManager().getFormalEventStartFailure(game);
            Utils.sendAdminError(sender, MessageConfig.EVENT_START_UNAVAILABLE
                    .replace("%game%", game.toString())
                    .replace("%detail%", reason == null ? "地图、执行端或比赛状态不满足启动条件" : reason));
        } else {
            Utils.sendAdminError(sender, MessageConfig.EVENT_START_NO_SCHEDULE);
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

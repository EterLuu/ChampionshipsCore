package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.ScheduleManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EventStartSubCommand extends BaseSubCommand {
    public EventStartSubCommand() {
        super("start", "开始正式比赛；进行中时再次执行会紧急停止",
                "/cc event start <游戏> [场地] [队伍1 队伍2]");
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

        if (game == GameTypeEnum.Dodgebolt)
            return startDodgebolt(sender, command, label, args);
        if (game == GameTypeEnum.DragonEggCarnival)
            return startDragonEggCarnival(sender, command, label, args);
        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }

        ScheduleManager.EventAction action = plugin.getScheduleManager().startOrStopFormalEvent(game);
        if (action == ScheduleManager.EventAction.STARTED) {
            Utils.sendAdminSuccess(sender, "正式比赛已开始准备：&#fff566" + game);
        } else if (action == ScheduleManager.EventAction.STOPPED) {
            Utils.sendAdminInfo(sender, "已通过重复 start 紧急停止正式比赛：&#fff566" + game);
        } else {
            Utils.sendAdminError(sender, "该游戏没有可用的正式比赛赛程");
        }
        return true;
    }

    private boolean startDragonEggCarnival(@NotNull CommandSender sender, @NotNull Command command,
                                            @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (plugin.getScheduleManager().stopFormalEvent(GameTypeEnum.DragonEggCarnival)) {
                Utils.sendAdminInfo(sender, "已通过重复 start 紧急停止龙蛋狂欢正式比赛");
            } else {
                sendUsage(sender);
            }
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage("&#bababa[&#fff566管理&#bababa] &#bababa用法 &#696969• &#ededed/cc event start dragoneggcarnival <队伍1> <队伍2>");
            return true;
        }
        ChampionshipTeam right = plugin.getTeamManager().getTeam(args[1]);
        ChampionshipTeam left = plugin.getTeamManager().getTeam(args[2]);
        if (right == null || left == null || right.equals(left)) {
            Utils.sendAdminError(sender, "请指定两支不同的有效队伍");
            return true;
        }
        ScheduleManager.EventAction action = plugin.getScheduleManager().startOrStopDragonEggCarnival(right, left);
        if (action == ScheduleManager.EventAction.STARTED)
            Utils.sendAdminSuccess(sender, "龙蛋狂欢正式比赛已开始准备");
        else
            Utils.sendAdminInfo(sender, "已通过重复 start 紧急停止龙蛋狂欢正式比赛");
        return true;
    }

    private boolean startDodgebolt(@NotNull CommandSender sender, @NotNull Command command,
                                   @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && plugin.getScheduleManager().stopFormalEvent(GameTypeEnum.Dodgebolt)) {
            Utils.sendAdminInfo(sender, "已通过重复 start 紧急停止躲避箭决赛准备");
            return true;
        }
        String area = null;
        ChampionshipTeam right = null;
        ChampionshipTeam left = null;
        int optionCount = args.length - 1;
        if (optionCount == 1) {
            area = args[1];
        } else if (optionCount == 2) {
            right = plugin.getTeamManager().getTeam(args[1]);
            left = plugin.getTeamManager().getTeam(args[2]);
        } else if (optionCount == 3) {
            area = args[1];
            right = plugin.getTeamManager().getTeam(args[2]);
            left = plugin.getTeamManager().getTeam(args[3]);
        } else if (optionCount != 0) {
            sendUsage(sender);
            return true;
        }
        if ((optionCount == 2 || optionCount == 3) && (right == null || left == null || right.equals(left))) {
            Utils.sendAdminError(sender, "请指定两支不同的有效队伍");
            return true;
        }
        plugin.getScheduleManager().requestDodgeboltFinal(area, right, left, sender);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1)
            return filterStartsWith(EventCommandSupport.enabledFormalGames(), args[0]);
        GameTypeEnum game = EventCommandSupport.parse(args[0]);
        if (game == GameTypeEnum.Dodgebolt) {
            if (args.length == 2) {
                List<String> values = new ArrayList<>(plugin.getGameManager().getDodgeboltManager().getAreaNameList());
                values.addAll(plugin.getTeamManager().getTeamNameList());
                return filterStartsWith(values, args[1]);
            }
            if (args.length == 3)
                return filterStartsWith(plugin.getTeamManager().getTeamNameList(), args[2]);
            if (args.length == 4) {
                List<String> teams = plugin.getTeamManager().getTeamNameList();
                teams.removeIf(name -> name.equalsIgnoreCase(args[2]));
                return filterStartsWith(teams, args[3]);
            }
        }
        if (game == GameTypeEnum.DragonEggCarnival) {
            if (args.length == 2)
                return filterStartsWith(plugin.getTeamManager().getTeamNameList(), args[1]);
            if (args.length == 3) {
                List<String> teams = plugin.getTeamManager().getTeamNameList();
                teams.removeIf(name -> name.equalsIgnoreCase(args[1]));
                return filterStartsWith(teams, args[2]);
            }
        }
        return Collections.emptyList();
    }
}

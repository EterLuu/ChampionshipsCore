package ink.ziip.championshipscore.command.game.start;

import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.schedule.TwoVTwoVector;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class BattleBoxStartSubCommand extends BaseSubCommand {
    public BattleBoxStartSubCommand() {
        super("battlebox", "开始战斗箱（两队对战）", "/cc game start battlebox <场地> <队伍1> <队伍2>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 3) {
            sendUsage(sender);
            return true;
        }
        if (args.length == 3) {
            String failed = MessageConfig.GAME_TEAM_GAME_START_FAILED
                    .replace("%team%", args[1])
                    .replace("%rival%", args[2])
                    .replace("%game%", GameTypeEnum.BattleBox.toString())
                    .replace("%area%", args[0]);

            BattleBoxArea battleBoxArea = plugin.getGameManager().getBattleBoxManager().getArea(args[0]);
            ChampionshipTeam rightChampionshipTeam = plugin.getTeamManager().getTeam(args[1]);
            ChampionshipTeam leftChampionshipTeam = plugin.getTeamManager().getTeam(args[2]);

            if (battleBoxArea != null && rightChampionshipTeam != null && leftChampionshipTeam != null) {
                if (plugin.getGameManager().joinBattleBoxArea(args[0], List.of(new TwoVTwoVector(rightChampionshipTeam, leftChampionshipTeam)))) {
                    String successful = MessageConfig.GAME_TEAM_GAME_START_SUCCESSFUL
                            .replace("%team%", rightChampionshipTeam.getColoredName())
                            .replace("%rival%", leftChampionshipTeam.getColoredName())
                            .replace("%game%", GameTypeEnum.BattleBox.toString())
                            .replace("%area%", args[0]);
                    sender.sendMessage(successful);
                } else {
                    sender.sendMessage(failed);
                }
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getGameManager().getBattleBoxManager().getAreaNameList();
            return filterStartsWith(returnList, args[0]);
        }
        if (args.length == 2) {
            List<String> returnList = plugin.getTeamManager().getTeamNameList();
            return filterStartsWith(returnList, args[1]);
        }
        if (args.length == 3) {
            List<String> returnList = plugin.getTeamManager().getTeamNameList();
            returnList.removeIf(s -> s != null && s.equalsIgnoreCase(args[1]));
            return filterStartsWith(returnList, args[2]);
        }
        return Collections.emptyList();
    }
}

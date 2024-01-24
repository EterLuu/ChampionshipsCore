package ink.ziip.championshipscore.command.game.start;

import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.Team;
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
        super("battlebox");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 3) {
            String failed = MessageConfig.GAME_TEAM_GAME_START_FAILED
                    .replace("%team%", args[1])
                    .replace("%rival%", args[2])
                    .replace("%game%", GameTypeEnum.BattleBox.toString())
                    .replace("%area%", args[0]);

            BattleBoxArea battleBoxArea = plugin.getGameManager().getBattleBoxManager().getArea(args[0]);
            Team rightTeam = plugin.getTeamManager().getTeam(args[1]);
            Team leftTeam = plugin.getTeamManager().getTeam(args[2]);

            if (battleBoxArea != null && rightTeam != null && leftTeam != null) {
                if (plugin.getGameManager().joinTeamArea(GameTypeEnum.BattleBox, args[0], rightTeam, leftTeam)) {
                    String successful = MessageConfig.GAME_TEAM_GAME_START_SUCCESSFUL
                            .replace("%team%", rightTeam.getColoredName())
                            .replace("%rival%", leftTeam.getColoredName())
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
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }
        if (args.length == 2) {
            List<String> returnList = plugin.getTeamManager().getTeamNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
            return returnList;
        }
        if (args.length == 3) {
            List<String> returnList = plugin.getTeamManager().getTeamNameList();
            returnList.removeIf(s -> s != null && (s.equals(args[1]) || !s.startsWith(args[2])));
            return returnList;
        }
        return Collections.emptyList();
    }
}

package ink.ziip.championshipscore.command.game.start;

import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class DragonEggCarnivalStartSubCommand extends BaseSubCommand {
    public DragonEggCarnivalStartSubCommand() {
        super("dragoncarnival");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 3) {
            String failed = MessageConfig.GAME_TEAM_GAME_START_FAILED
                    .replace("%team%", args[1])
                    .replace("%rival%", args[2])
                    .replace("%game%", GameTypeEnum.BattleBox.toString())
                    .replace("%area%", args[0]);

            DragonEggCarnivalArea dragonEggCarnivalArea = plugin.getGameManager().getDragonEggCarnivalManager().getArea(args[0]);
            ChampionshipTeam rightChampionshipTeam = plugin.getTeamManager().getTeam(args[1]);
            ChampionshipTeam leftChampionshipTeam = plugin.getTeamManager().getTeam(args[2]);

            if (dragonEggCarnivalArea != null && rightChampionshipTeam != null && leftChampionshipTeam != null) {
                if (plugin.getGameManager().joinTeamArea(GameTypeEnum.DragonEggCarnival, args[0], rightChampionshipTeam, leftChampionshipTeam)) {
                    String successful = MessageConfig.GAME_TEAM_GAME_START_SUCCESSFUL
                            .replace("%team%", rightChampionshipTeam.getColoredName())
                            .replace("%rival%", leftChampionshipTeam.getColoredName())
                            .replace("%game%", GameTypeEnum.DragonEggCarnival.toString())
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
            List<String> returnList = plugin.getGameManager().getDragonEggCarnivalManager().getAreaNameList();
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

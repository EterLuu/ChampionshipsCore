package ink.ziip.championshipscore.command.game.start;

import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HotyCodyDuskyStartSubCommand extends BaseSubCommand {
    public HotyCodyDuskyStartSubCommand() {
        super("hotycodydusky");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String failed = MessageConfig.GAME_SINGLE_GAME_START_FAILED
                .replace("%game%", GameTypeEnum.HotyCodyDusky.toString())
                .replace("%area%", args[0]);

        HotyCodyDuskyTeamArea hotyCodyDuskyTeamArea = plugin.getGameManager().getHotyCodyDuskyManager().getArea(args[0]);

        ChampionshipTeam[] teams = new ChampionshipTeam[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeam(args[i]);
            if (championshipTeam != null)
                teams[i - 1] = championshipTeam;
            else {
                sender.sendMessage(failed);
                return true;
            }
        }

        if (hotyCodyDuskyTeamArea != null) {
            if (plugin.getGameManager().joinSingleTeamAreaForTeams(GameTypeEnum.HotyCodyDusky, args[0], teams)) {
                String successful = MessageConfig.GAME_SINGLE_GAME_START_SUCCESSFUL
                        .replace("%game%", GameTypeEnum.HotyCodyDusky.toString())
                        .replace("%area%", args[0]);
                sender.sendMessage(successful);
            } else {
                sender.sendMessage(failed);
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> returnList;
        if (args.length == 1) {
            returnList = plugin.getGameManager().getHotyCodyDuskyManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
        } else {
            returnList = plugin.getTeamManager().getTeamNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[args.length - 1]));
        }
        return returnList;
    }
}

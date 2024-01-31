package ink.ziip.championshipscore.command.admin.schedule;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class ScheduleDragonEggCarnivalSubCommand extends BaseSubCommand {
    public ScheduleDragonEggCarnivalSubCommand() {
        super("dragoneggcarnival");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 2) {
            ChampionshipTeam rightChampionshipTeam = plugin.getTeamManager().getTeam(args[0]);
            ChampionshipTeam leftChampionshipTeam = plugin.getTeamManager().getTeam(args[1]);

            if (rightChampionshipTeam != null && leftChampionshipTeam != null) {
                plugin.getScheduleManager().startDragonEggCarnival(rightChampionshipTeam, leftChampionshipTeam);
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return plugin.getTeamManager().getTeamNameList();
        }
        if (args.length == 2) {
            List<String> returnList = plugin.getTeamManager().getTeamNameList();
            returnList.removeIf(s -> s != null && s.equals(args[0]));
            return returnList;
        }
        return Collections.emptyList();
    }
}

package ink.ziip.championshipscore.command.game.start;

import ink.ziip.championshipscore.api.game.advancementcc.AdvancementCCArea;
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

public class AdvancementCCStartSubCommand extends BaseSubCommand {
    public AdvancementCCStartSubCommand() {
        super("acc");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 2) {
            String failed = MessageConfig.GAME_SINGLE_TEAM_GAME_START_FAILED
                    .replace("%team%", args[1])
                    .replace("%game%", GameTypeEnum.AdvancementCC.toString())
                    .replace("%area%", args[0]);

            AdvancementCCArea advancementCCArea = plugin.getGameManager().getAdvancementCCManager().getArea(args[0]);
            ChampionshipTeam team = plugin.getTeamManager().getTeam(args[1]);

            if (advancementCCArea != null && team != null) {
                if (plugin.getGameManager().joinSingleTeamArea(GameTypeEnum.AdvancementCC, args[0], team)) {
                    String successful = MessageConfig.GAME_SINGLE_TEAM_GAME_START_SUCCESSFUL
                            .replace("%team%", team.getColoredName())
                            .replace("%game%", GameTypeEnum.AdvancementCC.toString())
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
            List<String> returnList = plugin.getGameManager().getAdvancementCCManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }
        if (args.length == 2) {
            List<String> returnList = plugin.getTeamManager().getTeamNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
            return returnList;
        }
        return Collections.emptyList();
    }
}

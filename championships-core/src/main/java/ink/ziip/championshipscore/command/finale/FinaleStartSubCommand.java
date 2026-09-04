package ink.ziip.championshipscore.command.finale;

import ink.ziip.championshipscore.api.finale.FinaleGameDefinition;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class FinaleStartSubCommand extends BaseSubCommand {
    private final FinaleGameDefinition definition;

    FinaleStartSubCommand(FinaleGameDefinition definition) {
        super("start", "按总榜前二或指定队伍启动正式决赛",
                "/cc finale " + definition.commandName() + " start <场地> [队伍1 队伍2]"
                        + (definition.supportsPartialRoster() ? " [--force]" : ""));
        this.definition = definition;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        boolean force = args.length > 0 && args[args.length - 1].equalsIgnoreCase("--force");
        if (force && !definition.supportsPartialRoster()) {
            sendUsage(sender);
            return true;
        }
        int optionCount = args.length - (force ? 1 : 0);
        if (optionCount != 1 && optionCount != 3) {
            sendUsage(sender);
            return true;
        }

        String area = args[0];
        ChampionshipTeam right = optionCount == 3 ? plugin.getTeamManager().getTeam(args[1]) : null;
        ChampionshipTeam left = optionCount == 3 ? plugin.getTeamManager().getTeam(args[2]) : null;
        if (optionCount == 3 && (right == null || left == null || right.equals(left))) {
            Utils.sendAdminError(sender, MessageConfig.FINALE_START_TEAM_REQUIRED);
            return true;
        }
        plugin.getScheduleManager().requestFinale(
                definition.gameType(), area, right, left, sender, force);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            var manager = plugin.getGameManager().getAreaManager(definition.gameType());
            return manager == null ? Collections.emptyList()
                    : filterStartsWith(manager.getAreaNameList(), args[0]);
        }
        if (args.length == 2)
            return filterStartsWith(plugin.getTeamManager().getTeamNameList(), args[1]);
        if (args.length == 3) {
            List<String> teams = new ArrayList<>(plugin.getTeamManager().getTeamNameList());
            teams.removeIf(name -> name.equalsIgnoreCase(args[1]));
            return filterStartsWith(teams, args[2]);
        }
        if (args.length == 4 && definition.supportsPartialRoster())
            return filterStartsWith(List.of("--force"), args[3]);
        return Collections.emptyList();
    }
}

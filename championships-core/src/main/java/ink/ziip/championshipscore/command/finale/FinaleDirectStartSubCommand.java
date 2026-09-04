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

final class FinaleDirectStartSubCommand extends BaseSubCommand {
    private final FinaleGameDefinition definition;

    FinaleDirectStartSubCommand(FinaleGameDefinition definition) {
        super("start-direct", "在指定场地直接开始决赛单局",
                "/cc finale " + definition.commandName() + " start-direct <场地> <队伍1> <队伍2>"
                        + (definition.supportsPartialRoster() ? " [--force]" : ""));
        this.definition = definition;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        boolean force = args.length == 4 && args[3].equalsIgnoreCase("--force");
        if ((args.length != 3 && args.length != 4)
                || (args.length == 4 && (!force || !definition.supportsPartialRoster()))) {
            sendUsage(sender);
            return true;
        }
        ChampionshipTeam right = plugin.getTeamManager().getTeam(args[1]);
        ChampionshipTeam left = plugin.getTeamManager().getTeam(args[2]);
        if (right == null || left == null || right.equals(left)) {
            Utils.sendAdminError(sender, MessageConfig.FINALE_DIRECT_START_INVALID);
            return true;
        }

        boolean started;
        if (definition.gameType() == ink.ziip.championshipscore.api.object.game.GameTypeEnum.Dodgebolt) {
            if (plugin.getGameManager().getDodgeboltManager().getArea(args[0]) == null) {
                Utils.sendAdminError(sender, MessageConfig.FINALE_DIRECT_START_INVALID);
                return true;
            }
            ChampionshipTeam higher = plugin.getRankManager().getCachedTeamPoints(right)
                    >= plugin.getRankManager().getCachedTeamPoints(left) ? right : left;
            started = plugin.getGameManager().joinDodgeboltArea(
                    args[0], right, left, higher, false, force);
        } else {
            started = plugin.getGameManager().joinTeamArea(
                    definition.gameType(), args[0], right, left);
        }

        if (started) {
            Utils.sendAdminSuccess(sender, (force
                    ? MessageConfig.FINALE_DIRECT_START_STARTED_FORCED
                    : MessageConfig.FINALE_DIRECT_START_STARTED)
                    .replace("%game%", definition.gameType().name()));
        } else {
            Utils.sendAdminError(sender, force
                    ? MessageConfig.FINALE_DIRECT_START_FORCED_FAILED
                    : MessageConfig.FINALE_DIRECT_START_FAILED);
        }
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

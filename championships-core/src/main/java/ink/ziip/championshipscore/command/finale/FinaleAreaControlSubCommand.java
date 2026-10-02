package ink.ziip.championshipscore.command.finale;

import ink.ziip.championshipscore.api.finale.FinaleGameDefinition;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.api.game.sulfursoccer.SulfurSoccerArea;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalArea;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/** Shared finale command surface for games without Dodgebolt's round controls. */
final class FinaleAreaControlSubCommand extends BaseSubCommand {
    private final FinaleGameDefinition definition;

    FinaleAreaControlSubCommand(FinaleGameDefinition definition, String name, String description) {
        super(name, description, "/cc finale " + definition.commandName() + " " + name + " <场地>"
                + ("force-win".equals(name) ? " <队伍>" : "eliminate".equals(name) ? " <玩家>" : ""));
        this.definition = definition;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        boolean teamArgument = "force-win".equals(commandName);
        boolean playerArgument = "eliminate".equals(commandName);
        int expectedArguments = teamArgument || playerArgument ? 2 : 1;
        if (args.length != expectedArguments) { sendUsage(sender); return true; }
        var manager = plugin.getGameManager().getAreaManager(definition.gameType());
        BaseGameInstance area = manager == null ? null : manager.getArea(args[0]);
        if (area == null) {
            Utils.sendAdminError(sender, MessageConfig.FINALE_DODGEBOLT_AREA_MISSING);
            return true;
        }
        boolean success = false;
        if (area instanceof SulfurSoccerArea sulfur) {
            success = switch (commandName) {
                case "pause" -> sulfur.pauseMatch();
                case "resume" -> sulfur.resumeMatch();
                case "restart-round" -> sulfur.restartCurrentRound();
                case "stop" -> { sulfur.endGame(); yield true; }
                case "force-win" -> {
                    ChampionshipTeam team = plugin.getTeamManager().getTeam(args[1]);
                    yield sulfur.forceChampion(team);
                }
                default -> false;
            };
        } else if (area instanceof DragonEggCarnivalArea dragon) {
            if ("force-win".equals(commandName)) {
                ChampionshipTeam team = plugin.getTeamManager().getTeam(args[1]);
                success = dragon.forceChampion(team);
            } else if ("stop".equals(commandName)) {
                dragon.endGame();
                success = true;
            }
        } else if ("stop".equals(commandName)) {
            area.endGame();
            success = true;
        }
        if (success) {
            Utils.sendAdminSuccess(sender, MessageConfig.FINALE_DODGEBOLT_CONTROL_EXECUTED.replace("%command%", commandName));
        } else {
            Utils.sendAdminError(sender, teamArgument
                    ? MessageConfig.FINALE_DODGEBOLT_FORCE_WIN_INVALID
                    : playerArgument ? MessageConfig.FINALE_DODGEBOLT_ELIMINATION_INVALID
                    : MessageConfig.FINALE_DODGEBOLT_CONTROL_STATE_DENIED);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            var manager = plugin.getGameManager().getAreaManager(definition.gameType());
            return manager == null ? Collections.emptyList() : filterStartsWith(manager.getAreaNameList(), args[0]);
        }
        if (args.length == 2 && "force-win".equals(commandName))
            return filterStartsWith(plugin.getTeamManager().getTeamNameList(), args[1]);
        if (args.length == 2 && "eliminate".equals(commandName)) {
            List<String> names = new java.util.ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return filterStartsWith(names, args[1]);
        }
        return Collections.emptyList();
    }
}

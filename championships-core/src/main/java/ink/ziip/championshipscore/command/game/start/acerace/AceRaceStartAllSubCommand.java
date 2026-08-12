package ink.ziip.championshipscore.command.game.start.acerace;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class AceRaceStartAllSubCommand extends BaseSubCommand {
    public AceRaceStartAllSubCommand() {
        super("all", "所有队伍开始王牌竞速", "/cc game start acerace all <场地>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }
        List<java.util.UUID> commandPlayers = sender instanceof Player player
                ? List.of(player.getUniqueId()) : List.of();
        String message = plugin.getGameManager().joinSingleTeamAreaForAllTeams(
                        GameTypeEnum.AceRace, args[0], commandPlayers)
                ? MessageConfig.GAME_SINGLE_GAME_START_SUCCESSFUL : MessageConfig.GAME_SINGLE_GAME_START_FAILED;
        sender.sendMessage(message.replace("%game%", GameTypeEnum.AceRace.toString()).replace("%area%", args[0]));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return filterStartsWith(plugin.getGameManager().getAceRaceManager().getAreaNameList(), args[0]);
        return Collections.emptyList();
    }
}

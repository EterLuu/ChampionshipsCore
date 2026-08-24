package ink.ziip.championshipscore.command.member;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.team.TeamManager;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class MemberAddSubCommand extends BaseSubCommand {
    public MemberAddSubCommand() {
        super("add", "向队伍添加成员", "/cc team member add <队伍ID> <玩家>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 2) {
            sendUsage(sender);
            return true;
        }
        if (args.length == 2) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeam(args[0]);
            if (championshipTeam == null) {
                String message = MessageConfig.MEMBER_ADDED_FAILED
                        .replace("%team%", args[0])
                        .replace("%player%", Utils.formatPlayerName(args[1]))
                        .replace("%reason%", MessageConfig.REASON_TEAM_DOES_NOT_EXIST);
                sender.sendMessage(message);
                return true;
            }
            plugin.getTeamManager().addTeamMember(args[1], championshipTeam).thenAccept(result -> {
                String message = result == TeamManager.MemberAddResult.ADDED
                        ? MessageConfig.MEMBER_SUCCESSFULLY_ADDED
                        .replace("%team%", championshipTeam.getColoredName())
                        .replace("%player%", Utils.formatPlayerNameOnly(args[1]))
                        : MessageConfig.MEMBER_ADDED_FAILED
                        .replace("%team%", args[0])
                        .replace("%player%", Utils.formatPlayerName(args[1]))
                        .replace("%reason%", reason(result));
                sender.sendMessage(message);
            });
        }
        return true;
    }

    private static String reason(TeamManager.MemberAddResult result) {
        return switch (result) {
            case INVALID_PLAYER_NAME -> MessageConfig.REASON_INVALID_PLAYER_NAME;
            case TEAM_NOT_FOUND -> MessageConfig.REASON_TEAM_DOES_NOT_EXIST;
            case TEAM_FULL -> MessageConfig.REASON_TEAM_FULL;
            case OPERATION_IN_PROGRESS -> MessageConfig.REASON_OPERATION_IN_PROGRESS;
            case PLAYER_NOT_FOUND -> MessageConfig.REASON_PLAYER_NOT_REGISTERED;
            case PROFILE_SERVICE_UNAVAILABLE -> MessageConfig.REASON_PROFILE_SERVICE_UNAVAILABLE;
            case IDENTITY_CONFLICT -> MessageConfig.REASON_IDENTITY_CONFLICT;
            case ALREADY_MEMBER -> MessageConfig.REASON_MEMBER_ALREADY_EXIST;
            case DATABASE_ERROR -> MessageConfig.REASON_DATABASE_ERROR;
            case ADDED -> "";
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getTeamManager().getTeamNameList();
            return filterStartsWith(returnList, args[0]);
        }

        if (args.length == 2) {
            List<String> returnList = plugin.getServer().getOnlinePlayers().stream()
                    .filter(player -> plugin.getTeamManager().getTeamByPlayer(player) == null)
                    .map(Player::getName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            return filterStartsWith(returnList, args[1]);
        }
        return Collections.emptyList();
    }
}

package ink.ziip.championshipscore.command.member;

import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class MemberDeleteSubCommand extends BaseSubCommand {
    public MemberDeleteSubCommand() {
        super("delete", "从队伍移除成员", "/cc team member delete <队伍ID> <玩家>");
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
                String message = MessageConfig.MEMBER_DELETED_FAILED
                        .replace("%team%", args[0])
                        .replace("%player%", Utils.formatPlayerName(args[1]))
                        .replace("%reason%", MessageConfig.REASON_TEAM_DOES_NOT_EXIST);
                sender.sendMessage(message);
                return true;
            }
            if (plugin.getTeamManager().deleteTeamMember(args[1], args[0])) {
                String message = MessageConfig.MEMBER_SUCCESSFULLY_DELETED
                        .replace("%team%", championshipTeam.getColoredName())
                        .replace("%player%", Utils.formatPlayerNameOnly(args[1]));
                sender.sendMessage(message);
            } else {
                String message = MessageConfig.MEMBER_DELETED_FAILED
                        .replace("%team%", args[0])
                        .replace("%player%", Utils.formatPlayerName(args[1]))
                        .replace("%reason%", MessageConfig.REASON_MEMBER_DOES_NOT_EXIST);
                sender.sendMessage(message);
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getTeamManager().getTeamNameList();
            return filterStartsWith(returnList, args[0]);
        }

        if (args.length == 2) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeam(args[0]);
            if (championshipTeam == null)
                return Collections.emptyList();
            List<String> returnList = championshipTeam.getTeamMemberNameList();
            return filterStartsWith(returnList, args[1]);
        }
        return Collections.emptyList();
    }
}

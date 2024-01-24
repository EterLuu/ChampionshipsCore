package ink.ziip.championshipscore.command.member;

import ink.ziip.championshipscore.api.team.Team;
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
        super("delete");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 2) {
            Team team = plugin.getTeamManager().getTeam(args[0]);
            if (team == null) {
                sender.sendMessage(String.format(MessageConfig.MEMBER_DELETED_FAILED, args[1], args[0], MessageConfig.REASON_TEAM_DOES_NOT_EXIST));
                return true;
            }
            if (plugin.getTeamManager().deleteTeamMember(args[1], args[0]))
                sender.sendMessage(String.format(MessageConfig.MEMBER_SUCCESSFULLY_DELETED, args[1], args[0]));
            else
                sender.sendMessage(String.format(MessageConfig.MEMBER_DELETED_FAILED, args[1], args[0], MessageConfig.REASON_MEMBER_DOES_NOT_EXIST));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getTeamManager().getTeamNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }

        if (args.length == 2) {
            Team team = plugin.getTeamManager().getTeam(args[0]);
            if (team == null)
                return Collections.emptyList();
            List<String> returnList = Utils.getPlayerNamesByUUIDs(team.getMembers());
            returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
            return returnList;
        }
        return Collections.emptyList();
    }
}

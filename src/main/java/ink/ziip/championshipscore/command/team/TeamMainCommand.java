package ink.ziip.championshipscore.command.team;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.member.MemberMainCommand;

public class TeamMainCommand extends BaseMainCommand {
    public TeamMainCommand() {
        super("team", "队伍与成员管理", ADMIN_PERMISSION);
        addSubCommand(new TeamAddSubCommand());
        addSubCommand(new TeamDeleteSubCommand());
        addSubCommand(new TeamInfoSubCommand());
        addSubCommand(new TeamListSubCommand());
        addSubCommand(new TeamTeleportationSubCommand());
        addSubCommand(new MemberMainCommand());
    }
}

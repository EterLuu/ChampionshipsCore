package ink.ziip.championshipscore.command.team;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class TeamMainCommand extends BaseMainCommand {
    public TeamMainCommand() {
        super("team");
        addSubCommand(new TeamAddSubCommand());
        addSubCommand(new TeamDeleteSubCommand());
        addSubCommand(new TeamInfoSubCommand());
    }
}

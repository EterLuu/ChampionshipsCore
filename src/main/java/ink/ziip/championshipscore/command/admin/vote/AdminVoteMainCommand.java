package ink.ziip.championshipscore.command.admin.vote;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class AdminVoteMainCommand extends BaseMainCommand {
    public AdminVoteMainCommand() {
        super("vote");
        addSubCommand(new AdminVoteStartSubCommand());
        addSubCommand(new AdminVoteEndSubCommand());
    }
}

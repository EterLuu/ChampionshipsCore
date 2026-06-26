package ink.ziip.championshipscore.command.admin.vote;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class AdminVoteMainCommand extends BaseMainCommand {
    public AdminVoteMainCommand() {
        super("vote", "投票管理");
        addSubCommand(new AdminVoteStartSubCommand());
        addSubCommand(new AdminVoteEndSubCommand());
    }
}

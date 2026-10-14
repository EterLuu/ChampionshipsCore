package ink.ziip.championshipscore.command.admin.vote;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

public class AdminVoteMainCommand extends BaseMainCommand {
    public AdminVoteMainCommand() {
        super("vote", MessageConfig.codeText("admin-vote-main-command.constructor-1"));
        addSubCommand(new AdminVoteStartSubCommand());
        addSubCommand(new AdminVoteEndSubCommand());
    }
}

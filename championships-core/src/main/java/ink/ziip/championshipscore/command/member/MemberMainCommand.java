package ink.ziip.championshipscore.command.member;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

public class MemberMainCommand extends BaseMainCommand {
    public MemberMainCommand() {
        super("member", MessageConfig.codeText("member-main-command.constructor-1"));
        addSubCommand(new MemberAddSubCommand());
        addSubCommand(new MemberDeleteSubCommand());
    }
}

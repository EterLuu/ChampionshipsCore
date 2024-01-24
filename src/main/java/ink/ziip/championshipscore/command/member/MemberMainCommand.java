package ink.ziip.championshipscore.command.member;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class MemberMainCommand extends BaseMainCommand {
    public MemberMainCommand() {
        super("member");
        addSubCommand(new MemberAddSubCommand());
        addSubCommand(new MemberDeleteSubCommand());
    }
}

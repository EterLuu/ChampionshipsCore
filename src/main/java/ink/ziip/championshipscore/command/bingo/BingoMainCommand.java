package ink.ziip.championshipscore.command.bingo;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class BingoMainCommand extends BaseMainCommand {
    public BingoMainCommand() {
        super("bingo");
        addSubCommand(new BingoStartSubCommand());
    }
}

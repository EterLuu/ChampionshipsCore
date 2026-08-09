package ink.ziip.championshipscore.command.game.start.bingo;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class BingoStartMainCommand extends BaseMainCommand {
    public BingoStartMainCommand() {
        super("bingo", "宾果");
        addSubCommand(new BingoStartAllSubCommand());
    }
}

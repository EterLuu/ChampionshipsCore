package ink.ziip.championshipscore.command.game.area.bingo;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class BingoAreaMainCommand extends BaseMainCommand {
    public BingoAreaMainCommand() {
        super("bingo", "宾果场地");
        addSubCommand(new BingoAreaAddSubCommand());
        addSubCommand(new BingoAreaSetSubCommand());
    }
}

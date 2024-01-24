package ink.ziip.championshipscore.command.game;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.game.area.AreaMainCommand;
import ink.ziip.championshipscore.command.game.start.GameStartMainCommand;

public class GameMainCommand extends BaseMainCommand {
    public GameMainCommand() {
        super("game");
        addSubCommand(new GameStartMainCommand());
        addSubCommand(new AreaMainCommand());
    }
}

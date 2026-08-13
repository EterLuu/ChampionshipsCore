package ink.ziip.championshipscore.command.game;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.game.start.GameStartMainCommand;
import ink.ziip.championshipscore.command.game.stop.GameStopSubCommand;

public class GameMainCommand extends BaseMainCommand {
    public GameMainCommand() {
        super("game", "启动单局或精确结束指定运行实例", ADMIN_PERMISSION);
        addSubCommand(new GameStartMainCommand());
        addSubCommand(new GameStopSubCommand());
    }
}

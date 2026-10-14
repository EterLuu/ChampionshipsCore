package ink.ziip.championshipscore.command.game;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.game.start.GameStartMainCommand;
import ink.ziip.championshipscore.command.game.stop.GameStopSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

public class GameMainCommand extends BaseMainCommand {
    public GameMainCommand() {
        super("game", MessageConfig.codeText("game-main-command.constructor-1"), ADMIN_PERMISSION);
        addSubCommand(new GameStartMainCommand());
        addSubCommand(new GameStopSubCommand());
    }
}

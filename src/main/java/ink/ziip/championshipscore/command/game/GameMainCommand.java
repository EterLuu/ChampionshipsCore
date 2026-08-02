package ink.ziip.championshipscore.command.game;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.game.start.GameStartMainCommand;

public class GameMainCommand extends BaseMainCommand {
    public GameMainCommand() {
        super("game", "直接启动单局（不创建正式赛程）", ADMIN_PERMISSION);
        addSubCommand(new GameStartMainCommand());
    }
}

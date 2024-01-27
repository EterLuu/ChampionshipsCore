package ink.ziip.championshipscore.command.game.area.snowball;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class SnowBallAreaMainCommand extends BaseMainCommand {
    public SnowBallAreaMainCommand() {
        super("snowball");
        addSubCommand(new SnowBallAreaAddSubCommand());
        addSubCommand(new SnowBallAreaSetSubCommand());
    }
}

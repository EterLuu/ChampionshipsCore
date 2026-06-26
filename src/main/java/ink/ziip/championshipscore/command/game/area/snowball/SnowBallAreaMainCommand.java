package ink.ziip.championshipscore.command.game.area.snowball;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class SnowBallAreaMainCommand extends BaseMainCommand {
    public SnowBallAreaMainCommand() {
        super("snowball", "雪球对决场地");
        addSubCommand(new SnowBallAreaAddSubCommand());
        addSubCommand(new SnowBallAreaSetSubCommand());
    }
}

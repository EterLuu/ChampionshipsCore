package ink.ziip.championshipscore.command.game.start.snowball;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class SnowBallStartMainCommand extends BaseMainCommand {
    public SnowBallStartMainCommand() {
        super("snowball", "雪球对决");
        addSubCommand(new SnowBallStartAllSubCommand());
    }
}

package ink.ziip.championshipscore.command.game.start.riptiderush;

import ink.ziip.championshipscore.command.BaseMainCommand;

public final class RiptideRushStartMainCommand extends BaseMainCommand {
    public RiptideRushStartMainCommand() {
        super("riptide", "激流勇进");
        addSubCommand(new RiptideRushStartAllSubCommand());
    }
}

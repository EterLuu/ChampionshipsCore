package ink.ziip.championshipscore.command.game.start.tntrun;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class TNTRunStartMainCommand extends BaseMainCommand {
    public TNTRunStartMainCommand() {
        super("tntrun");
        addSubCommand(new TNTRunStartAllSubCommand());
    }
}

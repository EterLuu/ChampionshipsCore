package ink.ziip.championshipscore.command.game.start.tgttos;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class TGTTOSStartMainCommand extends BaseMainCommand {
    public TGTTOSStartMainCommand() {
        super("tgttos", "到达彼岸");
        addSubCommand(new TGTTOSStartAllSubCommand());
    }
}

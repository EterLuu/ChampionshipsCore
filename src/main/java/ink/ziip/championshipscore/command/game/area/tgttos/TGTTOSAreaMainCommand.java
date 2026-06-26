package ink.ziip.championshipscore.command.game.area.tgttos;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class TGTTOSAreaMainCommand extends BaseMainCommand {
    public TGTTOSAreaMainCommand() {
        super("tgttos", "到达彼岸场地");
        addSubCommand(new TGTTOSAreaAddSubCommand());
        addSubCommand(new TGTTOSAreaSetSubCommand());
    }
}

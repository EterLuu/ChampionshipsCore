package ink.ziip.championshipscore.command.game.area.tgttos;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class TGTTOSAreaMainCommand extends BaseMainCommand {
    public TGTTOSAreaMainCommand() {
        super("tgttos");
        addSubCommand(new TGTTOSAreaAddSubCommand());
        addSubCommand(new TGTTOSAreaSetSubCommand());
    }
}

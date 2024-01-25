package ink.ziip.championshipscore.command.game.area.tntrun;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class TNTRunAreaMainCommand extends BaseMainCommand {
    public TNTRunAreaMainCommand() {
        super("tntrun");
        addSubCommand(new TNTRunAreaAddSubCommand());
        addSubCommand(new TNTRunAreaSetSubCommand());
        addSubCommand(new TNTRunAreaSaveSubCommand());
    }
}

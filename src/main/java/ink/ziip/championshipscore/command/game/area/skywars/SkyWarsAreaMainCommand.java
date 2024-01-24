package ink.ziip.championshipscore.command.game.area.skywars;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class SkyWarsAreaMainCommand extends BaseMainCommand {
    public SkyWarsAreaMainCommand() {
        super("skywars");
        addSubCommand(new SkyWarsAreaAddSubCommand());
        addSubCommand(new SkyWarsAreaSetSubCommand());
        addSubCommand(new SkyWarsAreaSaveSubCommand());
    }
}

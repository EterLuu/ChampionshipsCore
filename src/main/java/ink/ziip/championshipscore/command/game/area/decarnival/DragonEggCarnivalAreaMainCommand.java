package ink.ziip.championshipscore.command.game.area.decarnival;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class DragonEggCarnivalAreaMainCommand extends BaseMainCommand {
    public DragonEggCarnivalAreaMainCommand() {
        super("dragoneggcarnival");
        addSubCommand(new DragonEggCarnivalAreaAddSubCommand());
        addSubCommand(new DragonEggCarnivalAreaSaveSubCommand());
        addSubCommand(new DragonEggCarnivalAreaSetSubCommand());
    }
}

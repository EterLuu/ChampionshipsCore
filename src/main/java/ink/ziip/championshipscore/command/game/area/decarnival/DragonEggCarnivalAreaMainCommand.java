package ink.ziip.championshipscore.command.game.area.decarnival;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class DragonEggCarnivalAreaMainCommand extends BaseMainCommand {
    public DragonEggCarnivalAreaMainCommand() {
        super("dragoneggcarnival", "龙蛋嘉年华场地");
        addSubCommand(new DragonEggCarnivalAreaAddSubCommand());
        addSubCommand(new DragonEggCarnivalAreaSaveSubCommand());
        addSubCommand(new DragonEggCarnivalAreaSetSubCommand());
    }
}

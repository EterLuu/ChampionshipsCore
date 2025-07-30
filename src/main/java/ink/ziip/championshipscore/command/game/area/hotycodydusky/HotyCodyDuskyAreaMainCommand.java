package ink.ziip.championshipscore.command.game.area.hotycodydusky;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class HotyCodyDuskyAreaMainCommand extends BaseMainCommand {
    public HotyCodyDuskyAreaMainCommand() {
        super("hotycodydusky");
        addSubCommand(new HotyCodyDuskyAreaAddSubCommand());
        addSubCommand(new HotyCodyDuskyAreaSetSubCommand());
    }
}

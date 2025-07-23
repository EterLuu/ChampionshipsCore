package ink.ziip.championshipscore.command.game.area.parkourwarrior;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class ParkourWarriorAreaMainCommand extends BaseMainCommand {
    public ParkourWarriorAreaMainCommand() {
        super("parkourwarrior");
        addSubCommand(new ParkourWarriorAreaAddSubCommand());
        addSubCommand(new ParkourWarriorAreaSetSubCommand());
    }
}

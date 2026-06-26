package ink.ziip.championshipscore.command.game.area.parkourwarrior;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class ParkourWarriorAreaMainCommand extends BaseMainCommand {
    public ParkourWarriorAreaMainCommand() {
        super("parkourwarrior", "跑酷战士场地");
        addSubCommand(new ParkourWarriorAreaAddSubCommand());
        addSubCommand(new ParkourWarriorAreaSetSubCommand());
    }
}

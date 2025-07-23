package ink.ziip.championshipscore.command.game.start.parkourwarrior;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class ParkourWarriorStartMainCommand extends BaseMainCommand {
    public ParkourWarriorStartMainCommand() {
        super("parkourwarrior");
        addSubCommand(new ParkourWarriorStartAllSubCommand());
    }
}

package ink.ziip.championshipscore.command.game.area.parkourtag;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class ParkourTagAreaMainCommand extends BaseMainCommand {
    public ParkourTagAreaMainCommand() {
        super("parkourtag");
        addSubCommand(new ParkourTagAreaAddSubCommand());
        addSubCommand(new ParkourTagAreaSetSubCommand());
    }
}

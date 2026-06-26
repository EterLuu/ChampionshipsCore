package ink.ziip.championshipscore.command.game.area.parkourtag;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class ParkourTagAreaMainCommand extends BaseMainCommand {
    public ParkourTagAreaMainCommand() {
        super("parkourtag", "跑酷追逐场地");
        addSubCommand(new ParkourTagAreaAddSubCommand());
        addSubCommand(new ParkourTagAreaSetSubCommand());
    }
}

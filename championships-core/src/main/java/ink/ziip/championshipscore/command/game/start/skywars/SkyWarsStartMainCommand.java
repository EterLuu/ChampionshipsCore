package ink.ziip.championshipscore.command.game.start.skywars;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class SkyWarsStartMainCommand extends BaseMainCommand {
    public SkyWarsStartMainCommand() {
        super("skywars", "空岛乱斗");
        addSubCommand(new SkyWarsStartAllSubCommand());
    }
}

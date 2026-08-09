package ink.ziip.championshipscore.command.game.start.skywars;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class SkyWarsStartMainCommand extends BaseMainCommand {
    public SkyWarsStartMainCommand() {
        super("skywars", "空岛战争");
        addSubCommand(new SkyWarsStartAllSubCommand());
    }
}

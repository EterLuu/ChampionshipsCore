package ink.ziip.championshipscore.command.game.start.acerace;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class AceRaceStartMainCommand extends BaseMainCommand {
    public AceRaceStartMainCommand() {
        super("acerace", "王牌竞速");
        addSubCommand(new AceRaceStartAllSubCommand());
    }
}

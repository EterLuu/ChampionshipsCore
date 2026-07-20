package ink.ziip.championshipscore.command.game.start.buildmart;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class BuildMartStartMainCommand extends BaseMainCommand {
    public BuildMartStartMainCommand() {
        super("buildmart", "建材集市");
        addSubCommand(new BuildMartStartAllSubCommand());
    }
}

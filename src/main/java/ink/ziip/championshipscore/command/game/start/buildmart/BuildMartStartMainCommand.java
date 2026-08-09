package ink.ziip.championshipscore.command.game.start.buildmart;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class BuildMartStartMainCommand extends BaseMainCommand {
    public BuildMartStartMainCommand() {
        super("buildmart", "匹配赛建");
        addSubCommand(new BuildMartStartAllSubCommand());
    }
}

package ink.ziip.championshipscore.command.game.area.buildmart;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class BuildMartAreaMainCommand extends BaseMainCommand {
    public BuildMartAreaMainCommand() {
        super("buildmart", "建材集市场地");
        addSubCommand(new BuildMartAreaAddSubCommand());
        addSubCommand(new BuildMartAreaSetSubCommand());
        addSubCommand(new BuildMartSchematicSubCommand());
        addSubCommand(new BuildMartPrepareSubCommand());
        addSubCommand(new BuildMartBlueprintMainCommand());
    }
}

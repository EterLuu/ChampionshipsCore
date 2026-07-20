package ink.ziip.championshipscore.command.game.area.buildmart;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class BuildMartBlueprintMainCommand extends BaseMainCommand {
    public BuildMartBlueprintMainCommand() {
        super("blueprint", "建材集市蓝图管理");
        addSubCommand(new BuildMartBlueprintCreateSubCommand());
    }
}

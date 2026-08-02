package ink.ziip.championshipscore.command.map;

import ink.ziip.championshipscore.command.BaseMainCommand;

public final class BuildMartBlueprintMainCommand extends BaseMainCommand {
    public BuildMartBlueprintMainCommand() {
        super("blueprint", "建材集市蓝图管理");
        addSubCommand(new BuildMartBlueprintCreateSubCommand());
    }
}

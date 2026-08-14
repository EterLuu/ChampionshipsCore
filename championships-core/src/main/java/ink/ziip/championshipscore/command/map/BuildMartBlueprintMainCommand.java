package ink.ziip.championshipscore.command.map;

import ink.ziip.championshipscore.command.BaseMainCommand;

public final class BuildMartBlueprintMainCommand extends BaseMainCommand {
    public BuildMartBlueprintMainCommand() {
        super("blueprint", "匹配赛建蓝图管理");
        addSubCommand(new BuildMartBlueprintCreateSubCommand());
        addSubCommand(new BuildMartBlueprintAuditSubCommand("audit"));
        addSubCommand(new BuildMartBlueprintAuditSubCommand("preview"));
    }
}

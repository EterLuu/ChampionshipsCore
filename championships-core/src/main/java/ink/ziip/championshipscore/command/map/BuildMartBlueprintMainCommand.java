package ink.ziip.championshipscore.command.map;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

public final class BuildMartBlueprintMainCommand extends BaseMainCommand {
    public BuildMartBlueprintMainCommand() {
        super(
                "blueprint",
                MessageConfig.codeText("build-mart-blueprint-main-command.constructor-1"));
        addSubCommand(new BuildMartBlueprintCreateSubCommand());
        addSubCommand(new BuildMartBlueprintAuditSubCommand("audit"));
        addSubCommand(new BuildMartBlueprintAuditSubCommand("preview"));
    }
}

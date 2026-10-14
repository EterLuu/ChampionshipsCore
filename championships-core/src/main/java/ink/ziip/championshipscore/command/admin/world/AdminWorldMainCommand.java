package ink.ziip.championshipscore.command.admin.world;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

public class AdminWorldMainCommand extends BaseMainCommand {
    public AdminWorldMainCommand() {
        super("world", MessageConfig.codeText("admin-world-main-command.constructor-1"));
        addSubCommand(new WorldCreateSubCommand());
        addSubCommand(new WorldRenameSubCommand());
        addSubCommand(new WorldDeleteSubCommand());
        addSubCommand(new WorldUnloadSubCommand());
        addSubCommand(new WorldTeleportSubCommand());
        addSubCommand(new WorldListSubCommand());
    }
}

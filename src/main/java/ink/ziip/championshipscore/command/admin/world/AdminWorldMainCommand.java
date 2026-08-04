package ink.ziip.championshipscore.command.admin.world;

import ink.ziip.championshipscore.command.BaseMainCommand;

public class AdminWorldMainCommand extends BaseMainCommand {
    public AdminWorldMainCommand() {
        super("world", "世界管理");
        addSubCommand(new WorldCreateSubCommand());
        addSubCommand(new WorldRenameSubCommand());
        addSubCommand(new WorldDeleteSubCommand());
        addSubCommand(new WorldUnloadSubCommand());
        addSubCommand(new WorldTeleportSubCommand());
        addSubCommand(new WorldListSubCommand());
    }
}

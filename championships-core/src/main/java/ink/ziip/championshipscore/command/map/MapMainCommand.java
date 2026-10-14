package ink.ziip.championshipscore.command.map;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

/** Canonical map-editing entry point. */
public final class MapMainCommand extends BaseMainCommand {
    public MapMainCommand() {
        super("map", MessageConfig.codeText("map-main-command.constructor-1"), ADMIN_PERMISSION);
        addSubCommand(new MapEditSubCommand());
        addSubCommand(new MapRenameSubCommand());
        addSubCommand(new BuildMartBlueprintMainCommand());
    }
}

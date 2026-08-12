package ink.ziip.championshipscore.command.map;

import ink.ziip.championshipscore.command.BaseMainCommand;

/** Canonical map-editing entry point. */
public final class MapMainCommand extends BaseMainCommand {
    public MapMainCommand() {
        super("map", "地图与蓝图管理", ADMIN_PERMISSION);
        addSubCommand(new MapEditSubCommand());
        addSubCommand(new MapRenameSubCommand());
        addSubCommand(new BuildMartBlueprintMainCommand());
    }
}

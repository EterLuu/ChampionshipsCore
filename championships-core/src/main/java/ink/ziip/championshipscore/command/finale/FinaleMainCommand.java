package ink.ziip.championshipscore.command.finale;

import ink.ziip.championshipscore.api.finale.FinaleGameDefinition;
import ink.ziip.championshipscore.api.finale.FinaleGameRegistry;
import ink.ziip.championshipscore.command.BaseMainCommand;

/** Unified entry point for starting and operating championship finales. */
public final class FinaleMainCommand extends BaseMainCommand {
    public FinaleMainCommand() {
        super("finale", "决赛启动与现场控制", ADMIN_PERMISSION);
        for (FinaleGameDefinition definition : FinaleGameRegistry.definitions()) {
            addGameSubCommand(definition.gameType(), switch (definition.gameType()) {
                case Dodgebolt -> new FinaleDodgeboltMainCommand(definition);
                case DragonEggCarnival -> new FinaleDragonEggCarnivalMainCommand(definition);
                default -> throw new IllegalStateException("未实现决赛命令：" + definition.gameType());
            });
        }
    }
}

package ink.ziip.championshipscore.command.finale;

import ink.ziip.championshipscore.api.finale.FinaleGameDefinition;
import ink.ziip.championshipscore.command.BaseMainCommand;

final class FinaleDodgeboltMainCommand extends BaseMainCommand {
    FinaleDodgeboltMainCommand(FinaleGameDefinition definition) {
        super(definition.commandName(), "躲避箭决赛启动与裁判控制");
        addSubCommand(new FinaleStartSubCommand(definition));
        addSubCommand(new FinaleDirectStartSubCommand(definition));
        addSubCommand(new FinaleCancelSubCommand(definition));
        addSubCommand(new DodgeboltControlSubCommand("pause", "暂停决赛"));
        addSubCommand(new DodgeboltControlSubCommand("resume", "恢复决赛"));
        addSubCommand(new DodgeboltControlSubCommand("restart-round", "重开当前小局"));
        addSubCommand(new DodgeboltEliminateSubCommand());
        addSubCommand(new DodgeboltForceWinSubCommand());
        addSubCommand(new DodgeboltControlSubCommand("stop", "终止场内决赛，不产生冠军"));
    }
}

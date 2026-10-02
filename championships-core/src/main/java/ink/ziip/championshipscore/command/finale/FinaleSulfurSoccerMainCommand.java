package ink.ziip.championshipscore.command.finale;

import ink.ziip.championshipscore.api.finale.FinaleGameDefinition;
import ink.ziip.championshipscore.command.BaseMainCommand;

final class FinaleSulfurSoccerMainCommand extends BaseMainCommand {
    FinaleSulfurSoccerMainCommand(FinaleGameDefinition definition) {
        super(definition.commandName(), "硫方足球决赛启动与控制");
        addSubCommand(new FinaleStartSubCommand(definition));
        addSubCommand(new FinaleDirectStartSubCommand(definition));
        addSubCommand(new FinaleCancelSubCommand(definition));
        addSubCommand(new FinaleAreaControlSubCommand(definition, "pause", "暂停决赛"));
        addSubCommand(new FinaleAreaControlSubCommand(definition, "resume", "恢复决赛"));
        addSubCommand(new FinaleAreaControlSubCommand(definition, "restart-round", "重开当前小局"));
        addSubCommand(new FinaleAreaControlSubCommand(definition, "eliminate", "裁判判定选手出局"));
        addSubCommand(new FinaleAreaControlSubCommand(definition, "force-win", "裁判直接指定冠军"));
        addSubCommand(new FinaleAreaControlSubCommand(definition, "stop", "终止场内决赛，不产生冠军"));
    }
}

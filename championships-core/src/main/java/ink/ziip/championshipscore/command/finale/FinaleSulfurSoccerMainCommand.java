package ink.ziip.championshipscore.command.finale;

import ink.ziip.championshipscore.api.finale.FinaleGameDefinition;
import ink.ziip.championshipscore.command.BaseMainCommand;

final class FinaleSulfurSoccerMainCommand extends BaseMainCommand {
    FinaleSulfurSoccerMainCommand(FinaleGameDefinition definition) {
        super(definition.commandName(), "硫方足球决赛启动与控制");
        addSubCommand(new FinaleStartSubCommand(definition));
        addSubCommand(new FinaleDirectStartSubCommand(definition));
        addSubCommand(new FinaleCancelSubCommand(definition));
    }
}

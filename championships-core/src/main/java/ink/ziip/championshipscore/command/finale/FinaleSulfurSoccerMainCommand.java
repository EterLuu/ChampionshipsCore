package ink.ziip.championshipscore.command.finale;

import ink.ziip.championshipscore.api.finale.FinaleGameDefinition;
import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

final class FinaleSulfurSoccerMainCommand extends BaseMainCommand {
    FinaleSulfurSoccerMainCommand(FinaleGameDefinition definition) {
        super(
                definition.commandName(),
                MessageConfig.codeText("finale-sulfur-soccer-main-command.constructor-1"));
        addSubCommand(new FinaleStartSubCommand(definition));
        addSubCommand(new FinaleDirectStartSubCommand(definition));
        addSubCommand(new FinaleCancelSubCommand(definition));
        addSubCommand(
                new FinaleAreaControlSubCommand(
                        definition,
                        "pause",
                        MessageConfig.codeText("finale-sulfur-soccer-main-command.constructor-2")));
        addSubCommand(
                new FinaleAreaControlSubCommand(
                        definition,
                        "resume",
                        MessageConfig.codeText("finale-sulfur-soccer-main-command.constructor-3")));
        addSubCommand(
                new FinaleAreaControlSubCommand(
                        definition,
                        "restart-round",
                        MessageConfig.codeText("finale-sulfur-soccer-main-command.constructor-4")));
        addSubCommand(
                new FinaleAreaControlSubCommand(
                        definition,
                        "eliminate",
                        MessageConfig.codeText("finale-sulfur-soccer-main-command.constructor-5")));
        addSubCommand(
                new FinaleAreaControlSubCommand(
                        definition,
                        "force-win",
                        MessageConfig.codeText("finale-sulfur-soccer-main-command.constructor-6")));
        addSubCommand(
                new FinaleAreaControlSubCommand(
                        definition,
                        "stop",
                        MessageConfig.codeText("finale-sulfur-soccer-main-command.constructor-7")));
    }
}

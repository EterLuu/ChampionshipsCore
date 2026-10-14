package ink.ziip.championshipscore.command.finale;

import ink.ziip.championshipscore.api.finale.FinaleGameDefinition;
import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

final class FinaleDodgeboltMainCommand extends BaseMainCommand {
    FinaleDodgeboltMainCommand(FinaleGameDefinition definition) {
        super(
                definition.commandName(),
                MessageConfig.codeText("finale-dodgebolt-main-command.constructor-1"));
        addSubCommand(new FinaleStartSubCommand(definition));
        addSubCommand(new FinaleDirectStartSubCommand(definition));
        addSubCommand(new FinaleCancelSubCommand(definition));
        addSubCommand(
                new DodgeboltControlSubCommand(
                        "pause",
                        MessageConfig.codeText("finale-dodgebolt-main-command.constructor-2")));
        addSubCommand(
                new DodgeboltControlSubCommand(
                        "resume",
                        MessageConfig.codeText("finale-dodgebolt-main-command.constructor-3")));
        addSubCommand(
                new DodgeboltControlSubCommand(
                        "restart-round",
                        MessageConfig.codeText("finale-dodgebolt-main-command.constructor-4")));
        addSubCommand(new DodgeboltEliminateSubCommand());
        addSubCommand(new DodgeboltForceWinSubCommand());
        addSubCommand(
                new DodgeboltControlSubCommand(
                        "stop",
                        MessageConfig.codeText("finale-dodgebolt-main-command.constructor-5")));
    }
}

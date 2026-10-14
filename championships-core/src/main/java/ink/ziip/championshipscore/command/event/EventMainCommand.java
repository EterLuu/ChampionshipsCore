package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

/** Formal tournament lifecycle. Direct one-off games remain under {@code /cc game start}. */
public final class EventMainCommand extends BaseMainCommand {
    public EventMainCommand() {
        super(
                "event",
                MessageConfig.codeText("event-main-command.constructor-1"),
                ADMIN_PERMISSION);
        addSubCommand(new EventStartSubCommand());
        addSubCommand(new EventStopSubCommand());
        addSubCommand(new EventResetSubCommand());
        addSubCommand(new EventUndoSubCommand());
        addSubCommand(new EventTeamsMainCommand());
        addSubCommand(new EventExportSubCommand());
    }
}

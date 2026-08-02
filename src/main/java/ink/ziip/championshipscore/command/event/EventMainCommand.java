package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.command.BaseMainCommand;

/** Formal tournament lifecycle. Direct one-off games remain under {@code /cc game start}. */
public final class EventMainCommand extends BaseMainCommand {
    public EventMainCommand() {
        super("event", "正式比赛与赛程管理", ADMIN_PERMISSION);
        addSubCommand(new EventStartSubCommand());
        addSubCommand(new EventStopSubCommand());
        addSubCommand(new EventResetSubCommand());
        addSubCommand(new EventUndoSubCommand());
    }
}

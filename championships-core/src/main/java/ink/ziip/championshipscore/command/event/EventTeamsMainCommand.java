package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

public final class EventTeamsMainCommand extends BaseMainCommand {
    public EventTeamsMainCommand() {
        super(
                "teams",
                MessageConfig.codeText("event-teams-main-command.constructor-1"),
                ADMIN_PERMISSION);
        addSubCommand(new EventTeamImportSubCommand());
    }
}

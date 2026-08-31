package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.command.BaseMainCommand;

public final class EventTeamsMainCommand extends BaseMainCommand {
    public EventTeamsMainCommand() {
        super("teams", "从 cc-web 管理正式比赛阵容", ADMIN_PERMISSION);
        addSubCommand(new EventTeamImportSubCommand());
    }
}

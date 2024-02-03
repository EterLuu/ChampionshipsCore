package ink.ziip.championshipscore.command.admin;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.admin.schedule.ScheduleMainCommand;
import ink.ziip.championshipscore.command.admin.vote.AdminVoteMainCommand;

public class AdminMainCommand extends BaseMainCommand {
    public AdminMainCommand() {
        super("admin");
        addSubCommand(new AdminVoteMainCommand());
        addSubCommand(new ScheduleMainCommand());
        addSubCommand(new AdminSetMaxPlayerSubCommand());
    }
}

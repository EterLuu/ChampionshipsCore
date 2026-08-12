package ink.ziip.championshipscore.command.admin;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.admin.vote.AdminVoteMainCommand;
import ink.ziip.championshipscore.command.admin.world.AdminWorldMainCommand;

public class AdminMainCommand extends BaseMainCommand {
    public AdminMainCommand() {
        super("admin", "系统、投票与裁判管理", ADMIN_PERMISSION);
        addSubCommand(new AdminVoteMainCommand());
        addSubCommand(new AdminSetMaxPlayerSubCommand());
        addSubCommand(new AdminSudoSubCommand());
        addSubCommand(new AdminTeleportationSubCommand());
        addSubCommand(new AdminReloadSubCommand());
        addSubCommand(new AdminVisibilitySubCommand());
        addSubCommand(new AdminWorldMainCommand());
    }
}

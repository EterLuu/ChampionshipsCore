package ink.ziip.championshipscore.command.admin;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.admin.vote.AdminVoteMainCommand;
import ink.ziip.championshipscore.command.admin.world.AdminWorldMainCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

public class AdminMainCommand extends BaseMainCommand {
    public AdminMainCommand() {
        super(
                "admin",
                MessageConfig.codeText("admin-main-command.constructor-1"),
                ADMIN_PERMISSION);
        addSubCommand(new AdminVoteMainCommand());
        addSubCommand(new AdminSetMaxPlayerSubCommand());
        addSubCommand(new AdminSudoSubCommand());
        addSubCommand(new AdminTeleportationSubCommand());
        addSubCommand(new AdminReloadSubCommand());
        addSubCommand(new AdminVisibilitySubCommand());
        addSubCommand(new AdminWorldMainCommand());
        addSubCommand(new AdminMuteSubCommand(AdminMuteSubCommand.Action.MUTE));
        addSubCommand(new AdminMuteSubCommand(AdminMuteSubCommand.Action.TEMPMUTE));
        addSubCommand(new AdminMuteSubCommand(AdminMuteSubCommand.Action.UNMUTE));
    }
}

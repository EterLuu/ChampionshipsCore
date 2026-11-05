package ink.ziip.championshipscore.command.team;

import ink.ziip.championshipscore.api.team.gui.TeamManagementMenu;
import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.member.MemberMainCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TeamMainCommand extends BaseMainCommand {
    private final TeamManagementMenu menu;

    public TeamMainCommand() {
        super("team", MessageConfig.codeText("team-main-command.constructor-1"), TEST_PERMISSION);
        menu = new TeamManagementMenu(plugin);
        addSubCommand(new TeamAddSubCommand());
        addSubCommand(new TeamDeleteSubCommand());
        addSubCommand(new TeamInfoSubCommand());
        addSubCommand(new TeamListSubCommand());
        addSubCommand(new TeamTeleportationSubCommand());
        addSubCommand(new TeamTestSubCommand());
        addSubCommand(new MemberMainCommand());
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            if (sender.hasPermission(ADMIN_PERMISSION) && sender instanceof Player player) {
                menu.openOverview(player, 0);
            } else {
                super.onCommand(sender, command, label, args);
            }
            return true;
        }
        return super.onCommand(sender, command, label, args);
    }

    @Override
    protected boolean canUse(@NotNull CommandSender sender, @NotNull BaseMainCommand subCommand) {
        if ("test".equalsIgnoreCase(subCommand.getCommandName()))
            return sender.hasPermission(TEST_PERMISSION) || sender.hasPermission(ADMIN_PERMISSION);
        return sender.hasPermission(ADMIN_PERMISSION);
    }
}

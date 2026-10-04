package ink.ziip.championshipscore.command.admin;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class AdminSetMaxPlayerSubCommand extends BaseSubCommand {
    public AdminSetMaxPlayerSubCommand() {
        super("set-max-player", "设置每场游戏的最大玩家数", "/cc admin set-max-player <数量>");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }
        final int maxPlayers;
        try {
            maxPlayers = Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            CoreMessages.sendAdminError(sender, MessageConfig.ADMIN_MAX_PLAYERS_POSITIVE_INTEGER);
            return true;
        }
        if (maxPlayers < 1) {
            CoreMessages.sendAdminError(sender, MessageConfig.ADMIN_MAX_PLAYERS_GREATER_THAN_ZERO);
            return true;
        }
        CCConfig.MAX_PLAYERS = maxPlayers;
        plugin.getConfigurationManager().getCCConfig().saveOptions();
        CoreMessages.sendAdminSuccess(
                sender,
                MessageConfig.ADMIN_MAX_PLAYERS_SET.replace("%count%", String.valueOf(maxPlayers)));

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        return Collections.emptyList();
    }
}

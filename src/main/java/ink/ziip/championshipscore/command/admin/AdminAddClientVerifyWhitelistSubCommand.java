package ink.ziip.championshipscore.command.admin;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AdminAddClientVerifyWhitelistSubCommand extends BaseSubCommand {
    public AdminAddClientVerifyWhitelistSubCommand() {
        super("add-client-verify-whitelist");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            try {
                CCConfig.CLIENT_VERIFY_API_WHITELIST.add(args[0]);
                plugin.getConfigurationManager().getCCConfig().saveOptions();
            } catch (Exception ignored) {
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }

        return Collections.emptyList();
    }
}

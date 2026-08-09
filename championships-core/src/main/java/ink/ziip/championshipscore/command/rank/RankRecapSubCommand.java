package ink.ziip.championshipscore.command.rank;

import ink.ziip.championshipscore.command.BaseSubCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class RankRecapSubCommand extends BaseSubCommand {
    public RankRecapSubCommand() {
        super("recap", "重看最近一次结算", "/cc rank recap");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 0) {
            sendUsage(sender);
            return true;
        }
        if (sender instanceof Player player)
            plugin.getRankManager().sendLatestRankingSummary(player);
        else
            sender.sendMessage(plugin.getRankManager().getTeamRankString());
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

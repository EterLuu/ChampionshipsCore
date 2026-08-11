package ink.ziip.championshipscore.command.admin;

import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/** Read-only visibility diagnostics for live players. */
public final class AdminVisibilitySubCommand extends BaseSubCommand {
    public AdminVisibilitySubCommand() {
        super("visibility", "查看玩家实体显隐状态与原因", "/cc admin visibility [player]");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length > 1) {
            sendUsage(sender);
            return true;
        }
        Player target;
        if (args.length == 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                Utils.sendAdminError(sender, "玩家不在线：" + args[0]);
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sendUsage(sender);
            return true;
        }

        sender.sendMessage("§6[玩家显隐] §f" + target.getName() + " §7(" + target.getUniqueId() + ")");
        for (String line : plugin.getVisibilityManager().describe(target.getUniqueId()))
            sender.sendMessage("§7- §f" + line);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1)
            return complete(Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList(), args[0]);
        return Collections.emptyList();
    }
}

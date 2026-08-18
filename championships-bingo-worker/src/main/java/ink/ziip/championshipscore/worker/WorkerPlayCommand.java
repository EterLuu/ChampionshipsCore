package ink.ziip.championshipscore.worker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Worker-side bridge for the /cc command: free-play leave and admin match stop. */
final class WorkerPlayCommand implements CommandExecutor {
    private final WorkerMatchRegistry registry;

    WorkerPlayCommand(WorkerMatchRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("stop")) {
            if (!sender.hasPermission("cc.admin")) {
                sender.sendMessage(Component.text("[Bingo] ", NamedTextColor.AQUA)
                        .append(Component.text("你没有权限执行该指令。", NamedTextColor.RED)));
                return true;
            }
            if (args.length == 3 && args[2].equalsIgnoreCase("--confirm")) {
                registry.requestAdminStop(sender);
            } else {
                sender.sendMessage(Component.text("[Bingo] ", NamedTextColor.AQUA)
                        .append(Component.text("用法：/cc game stop --confirm（结束当前比赛并按成绩结算）",
                                NamedTextColor.GRAY)));
            }
            return true;
        }
        if (sender instanceof Player player
                && args.length == 2 && args[0].equalsIgnoreCase("play") && args[1].equalsIgnoreCase("leave")) {
            registry.requestVoluntaryLeave(player);
            return true;
        }
        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        WorkerMatchSession session = registry.activeSession();
        if (session == null || session.state().terminal()) {
            sender.sendMessage(Component.text("[Bingo] ", NamedTextColor.AQUA)
                    .append(Component.text("当前没有进行中的比赛。", NamedTextColor.GRAY)));
        } else if (session.eventMode()) {
            sender.sendMessage(Component.text("[赛事] ", NamedTextColor.GOLD)
                    .append(Component.text("正式赛事进行中，比赛结束后将自动返回大厅。", NamedTextColor.GRAY)));
        } else {
            sender.sendMessage(Component.text("[自由游玩] ", NamedTextColor.GREEN)
                    .append(Component.text("请使用 ", NamedTextColor.GRAY))
                    .append(Component.text("/cc play leave", NamedTextColor.YELLOW))
                    .append(Component.text(" 离开当前游戏。", NamedTextColor.GRAY)));
        }
        if (sender.hasPermission("cc.admin")) {
            sender.sendMessage(Component.text("[Bingo] ", NamedTextColor.AQUA)
                    .append(Component.text("管理员：/cc game stop --confirm 结束当前比赛。", NamedTextColor.GRAY)));
        }
    }
}

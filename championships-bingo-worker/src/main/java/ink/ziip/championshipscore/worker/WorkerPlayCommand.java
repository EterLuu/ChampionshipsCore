package ink.ziip.championshipscore.worker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Minimal worker-side bridge for leaving a remotely hosted free-play Bingo match. */
final class WorkerPlayCommand implements CommandExecutor {
    private final WorkerMatchRegistry registry;

    WorkerPlayCommand(WorkerMatchRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 2 && args[0].equalsIgnoreCase("play") && args[1].equalsIgnoreCase("leave")) {
            registry.requestVoluntaryLeave(player);
            return true;
        }
        player.sendMessage(Component.text("[自由游玩] ", NamedTextColor.GREEN)
                .append(Component.text("请使用 ", NamedTextColor.GRAY))
                .append(Component.text("/cc play leave", NamedTextColor.YELLOW))
                .append(Component.text(" 离开当前游戏。", NamedTextColor.GRAY)));
        return true;
    }
}

package ink.ziip.championshipscore.worker;

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
                registry.sendConfiguredMessage(sender, "worker.command.no-permission");
                return true;
            }
            if (args.length == 3 && args[2].equalsIgnoreCase("--confirm")) {
                registry.requestAdminStop(sender);
            } else {
                registry.sendConfiguredMessage(sender, "worker.command.admin-stop-usage");
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
            registry.sendConfiguredMessage(sender, "worker.command.no-active-match");
        } else if (session.eventMode()) {
            registry.sendConfiguredMessage(sender, "worker.command.event-active");
        } else {
            registry.sendConfiguredMessage(sender, "worker.command.free-play-help");
        }
        if (sender.hasPermission("cc.admin")) {
            registry.sendConfiguredMessage(sender, "worker.command.admin-stop-help");
        }
    }
}

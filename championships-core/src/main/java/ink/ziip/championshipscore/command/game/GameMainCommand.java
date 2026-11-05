package ink.ziip.championshipscore.command.game;

import ink.ziip.championshipscore.command.BaseMainCommand;
import ink.ziip.championshipscore.command.game.start.GameStartMainCommand;
import ink.ziip.championshipscore.command.game.stop.GameStopSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GameMainCommand extends BaseMainCommand {
    public GameMainCommand() {
        // Child commands enforce their own permissions. Automated test controls use a
        // dedicated permission and are intentionally separate from server administration.
        super("game", MessageConfig.codeText("game-main-command.constructor-1"));
        addSubCommand(new GameStartMainCommand());
        addSubCommand(new GameStopSubCommand());
        addSubCommand(new TestMainCommand());
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        // The parent node accepts both admin and test permissions so the test child can
        // be reached. Preserve the old root-level access boundary for ordinary players.
        if (!sender.hasPermission(ADMIN_PERMISSION)
                && !sender.hasPermission(TEST_AUTOMATION_PERMISSION)) {
            sender.sendMessage(MessageConfig.NO_PERMISSION);
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender, true);
            return true;
        }
        return super.onCommand(sender, command, label, args);
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)
                && !sender.hasPermission(TEST_AUTOMATION_PERMISSION))
            return List.of();
        return super.onTabComplete(sender, command, label, args);
    }
}

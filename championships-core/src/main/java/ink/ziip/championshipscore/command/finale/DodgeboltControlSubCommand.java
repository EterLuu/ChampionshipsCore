package ink.ziip.championshipscore.command.finale;

import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltArea;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

final class DodgeboltControlSubCommand extends BaseSubCommand {
    DodgeboltControlSubCommand(String name, String description) {
        super(name, description, "/cc finale dodgebolt " + name + " <场地>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }
        DodgeboltArea area = plugin.getGameManager().getDodgeboltManager().getArea(args[0]);
        if (area == null) {
            Utils.sendAdminError(sender, MessageConfig.FINALE_DODGEBOLT_AREA_MISSING);
            return true;
        }
        boolean success = switch (commandName) {
            case "pause" -> area.pauseMatch(null);
            case "resume" -> area.resumeMatch();
            case "restart-round" -> area.restartCurrentRound();
            case "stop" -> area.stopMatch();
            default -> false;
        };
        if (success) Utils.sendAdminSuccess(sender, MessageConfig.FINALE_DODGEBOLT_CONTROL_EXECUTED.replace("%command%", commandName));
        else Utils.sendAdminError(sender, MessageConfig.FINALE_DODGEBOLT_CONTROL_STATE_DENIED);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1)
            return filterStartsWith(plugin.getGameManager().getDodgeboltManager().getAreaNameList(), args[0]);
        return Collections.emptyList();
    }
}

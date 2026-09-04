package ink.ziip.championshipscore.command.finale;

import ink.ziip.championshipscore.api.finale.FinaleGameDefinition;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

final class FinaleCancelSubCommand extends BaseSubCommand {
    private final FinaleGameDefinition definition;

    FinaleCancelSubCommand(FinaleGameDefinition definition) {
        super("cancel", "取消决赛准备或强制结束正式决赛",
                "/cc finale " + definition.commandName() + " cancel");
        this.definition = definition;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 0) {
            sendUsage(sender);
            return true;
        }
        if (plugin.getScheduleManager().stopFinale(definition.gameType()))
            Utils.sendAdminSuccess(sender, MessageConfig.FINALE_CANCELLED.replace("%game%", definition.gameType().name()));
        else
            Utils.sendAdminInfo(sender, MessageConfig.FINALE_NOT_RUNNING.replace("%game%", definition.gameType().name()));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

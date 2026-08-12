package ink.ziip.championshipscore.command.finale;

import ink.ziip.championshipscore.api.finale.FinaleGameDefinition;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
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
            Utils.sendAdminSuccess(sender, "已取消正式决赛：&#fff566" + definition.gameType());
        else
            Utils.sendAdminInfo(sender, "该正式决赛当前未运行：&#fff566" + definition.gameType());
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

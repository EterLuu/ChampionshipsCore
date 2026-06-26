package ink.ziip.championshipscore.command.admin.schedule;

import ink.ziip.championshipscore.command.BaseSubCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class ScheduleBattleBoxSubCommand extends BaseSubCommand {
    public ScheduleBattleBoxSubCommand() {
        super("battlebox", "按赛程开始战斗箱", "/cc admin schedule battlebox");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        plugin.getScheduleManager().getBattleBoxScheduleManager().startBattleBox();

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

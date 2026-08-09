package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class EventUndoSubCommand extends BaseSubCommand {
    public EventUndoSubCommand() {
        super("undo", "撤销最近正式比赛的轮次与成绩", "/cc event undo --confirm");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("--confirm")) {
            sendUsage(sender);
            return true;
        }
        GameTypeEnum latest = plugin.getScheduleManager().deleteLatestGame();
        if (latest == null)
            Utils.sendAdminError(sender, "没有可撤销的正式比赛");
        else
            Utils.sendAdminSuccess(sender, "正在撤销 &#fff566" + latest + " &#696969• 约 3 秒后清除轮次与成绩");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return complete(List.of("--confirm"), args[0]);
        return Collections.emptyList();
    }
}

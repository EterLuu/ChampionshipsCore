package ink.ziip.championshipscore.command.admin.schedule;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Undo the most recently started scheduled game: stops its schedule + running areas, then (after a short
 * delay so async score recording lands first) clears its status entry and point records. Lets an admin
 * replay a broken game without reloading the plugin or hand-editing the database.
 *
 * <p>Dangerous: like {@code reload}, requires one extra argument (any value) so tab-completing the command
 * and pressing enter doesn't accidentally trigger it. Usage: {@code /cc admin schedule delete <任意参数>}.
 */
public class ScheduleDeleteSubCommand extends BaseSubCommand {
    public ScheduleDeleteSubCommand() {
        super("delete", "撤销当前游戏的轮次与成绩", "/cc admin schedule delete <任意参数>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            Utils.sendAdminInfo(sender, "用法 #696969• #ededed/cc admin schedule delete <确认参数>");
            return true;
        }
        GameTypeEnum latest = plugin.getScheduleManager().deleteLatestGame();
        if (latest == null) {
            Utils.sendAdminError(sender, "没有可撤销的轮次");
        } else {
            Utils.sendAdminSuccess(sender, "正在撤销 #fff566" + latest + " #696969• 约 3 秒后清除轮次与成绩");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

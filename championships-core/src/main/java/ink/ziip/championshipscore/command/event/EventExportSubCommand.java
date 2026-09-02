package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.api.event.ChampionshipResultsExporter;
import ink.ziip.championshipscore.api.event.EventStateStore;
import ink.ziip.championshipscore.api.rank.ChampionshipArchiveSnapshot;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public final class EventExportSubCommand extends BaseSubCommand {
    public EventExportSubCommand() {
        super("export", "导出当前正式比赛的完整积分 JSON", "/cc event export");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 0) {
            sendUsage(sender);
            return true;
        }
        EventStateStore.ActiveEvent active = new EventStateStore(plugin).load();
        if (active == null) {
            Utils.sendAdminError(sender, "没有通过 cc-web 阵容链接导入的当前赛事");
            return true;
        }
        if (plugin.getScheduleManager().hasRunningFormalEvent()) {
            Utils.sendAdminError(sender, "仍有正式比赛赛程运行，请先完成或停止比赛");
            return true;
        }
        if (plugin.getRankManager().getRound() < 1) {
            Utils.sendAdminError(sender, "当前赛事没有已结算的正式比赛积分");
            return true;
        }
        Utils.sendAdminInfo(sender, "正在汇总团队与个人逐游戏积分");
        plugin.getRankManager().createChampionshipArchiveSnapshot().whenComplete((snapshot, failure) -> {
            if (failure != null) {
                error(sender, failure.getMessage());
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(sender, active, snapshot));
        });
        return true;
    }

    private void write(CommandSender sender, EventStateStore.ActiveEvent active,
                       ChampionshipArchiveSnapshot snapshot) {
        try {
            Path exported = ChampionshipResultsExporter.export(plugin.getDataFolder().toPath(), active.slug(), snapshot);
            Bukkit.getScheduler().runTask(plugin, () -> Utils.sendAdminSuccess(sender,
                    "赛事“" + active.title() + "”积分已导出：" + snapshot.teams().size()
                            + " 支队伍，" + snapshot.players().size() + " 名玩家；文件：" + exported));
        } catch (Exception failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to export event scores", failure);
            error(sender, failure.getMessage());
        }
    }

    private void error(CommandSender sender, String message) {
        String detail = message == null || message.isBlank() ? "未知错误" : message;
        Bukkit.getScheduler().runTask(plugin, () -> Utils.sendAdminError(sender, "积分导出失败：" + detail));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

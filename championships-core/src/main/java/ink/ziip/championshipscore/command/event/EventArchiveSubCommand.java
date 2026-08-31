package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.api.event.EventStateStore;
import ink.ziip.championshipscore.api.rank.ChampionshipArchiveSnapshot;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class EventArchiveSubCommand extends BaseSubCommand {
    public EventArchiveSubCommand() {
        super("archive", "归档当前正式比赛的完整积分到 cc-web", "/cc event archive --confirm");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("--confirm")) {
            sendUsage(sender);
            return true;
        }
        EventStateStore store = new EventStateStore(plugin);
        EventStateStore.ActiveEvent active = store.load();
        if (active == null) {
            Utils.sendAdminError(sender, "没有通过 cc-web 阵容链接导入的当前赛事");
            return true;
        }
        if (active.archived()) {
            Utils.sendAdminError(sender, "当前赛事已完成归档，不能重复上传");
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
        Utils.sendAdminInfo(sender, "正在汇总团队与个人逐游戏积分并上传 cc-web");
        plugin.getRankManager().createChampionshipArchiveSnapshot().whenComplete((snapshot, failure) -> {
            if (failure != null) {
                error(sender, failure.getMessage());
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> upload(sender, store, active, snapshot));
        });
        return true;
    }

    private void upload(CommandSender sender, EventStateStore store,
                        EventStateStore.ActiveEvent active, ChampionshipArchiveSnapshot snapshot) {
        try {
            EventCommandSupport.webClient().archive(active.slug(), snapshot);
            if (!store.markArchived()) {
                error(sender, "成绩已上传，但本地归档标记写入失败；不要重复执行命令");
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> Utils.sendAdminSuccess(sender,
                    "赛事“" + active.title() + "”已归档：" + snapshot.teams().size()
                            + " 支队伍，" + snapshot.players().size() + " 名玩家"));
        } catch (Exception failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to archive event scores to cc-web", failure);
            error(sender, failure.getMessage());
        }
    }

    private void error(CommandSender sender, String message) {
        String detail = message == null || message.isBlank() ? "未知错误" : message;
        Bukkit.getScheduler().runTask(plugin, () -> Utils.sendAdminError(sender, "成绩归档失败：" + detail));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return complete(List.of("--confirm"), args[0]);
        return Collections.emptyList();
    }
}

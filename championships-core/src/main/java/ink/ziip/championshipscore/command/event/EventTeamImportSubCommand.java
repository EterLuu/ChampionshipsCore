package ink.ziip.championshipscore.command.event;

import ink.ziip.championshipscore.api.event.EventStateStore;
import ink.ziip.championshipscore.api.event.EventTeamImport;
import ink.ziip.championshipscore.api.team.entry.TeamImportEntry;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class EventTeamImportSubCommand extends BaseSubCommand {
    public EventTeamImportSubCommand() {
        super("import", "清空旧赛事状态并从 cc-web 导入完整阵容",
                "/cc event teams import <cc-web链接> --confirm");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 2 || !args[1].equalsIgnoreCase("--confirm")) {
            sendUsage(sender);
            return true;
        }
        if (plugin.getScheduleManager().hasRunningFormalEvent()) {
            Utils.sendAdminError(sender, "正式比赛正在运行，不能替换队伍");
            return true;
        }
        Utils.sendAdminInfo(sender, "正在从 cc-web 校验阵容；成功后会结束上一届有效积分并整体替换队伍");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                EventTeamImport imported = EventCommandSupport.webClient().fetchTeamImport(args[0]);
                List<TeamImportEntry> teams = EventCommandSupport.validateImport(imported);
                plugin.getTeamManager().replaceFormalTeams(teams).whenComplete((replaced, failure) -> {
                    if (failure != null || !Boolean.TRUE.equals(replaced)) {
                        error(sender, failure == null ? "队伍数据库事务失败或比赛状态已变化" : failure.getMessage());
                        return;
                    }
                    try {
                        new EventStateStore(plugin).save(imported.event());
                        plugin.getRankManager().reloadActiveEventScoring();
                    } catch (Exception stateFailure) {
                        plugin.getLogger().warning("Teams imported but active event state could not be saved: "
                                + stateFailure.getMessage());
                        error(sender, "队伍已导入，但赛事状态文件写入失败；请勿开始比赛并检查日志");
                        return;
                    }
                    success(sender, "已导入赛事“" + imported.event().title() + "”：" + teams.size() + " 支队伍，"
                            + teams.stream().mapToInt(team -> team.members().size()).sum() + " 名玩家");
                });
            } catch (Exception failure) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Unable to import cc-web event teams", failure);
                error(sender, failure.getMessage());
            }
        });
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 2) return complete(List.of("--confirm"), args[1]);
        return Collections.emptyList();
    }

    private void success(CommandSender sender, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> Utils.sendAdminSuccess(sender, message));
    }

    private void error(CommandSender sender, String message) {
        String detail = message == null || message.isBlank() ? "未知错误" : message;
        Bukkit.getScheduler().runTask(plugin, () -> Utils.sendAdminError(sender, "阵容导入失败：" + detail));
    }
}

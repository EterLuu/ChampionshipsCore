package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The user-facing command index. Keep each canonical command and its help copy together here;
 * legacy routes deliberately do not appear in this catalogue.
 */
public final class CommandCatalog {
    private record Entry(String usage, String description) {}

    private static final List<Entry> PLAYER_COMMANDS = List.of(
            new Entry("/cc spawn", "回到大厅（游戏或观战期间不可用）"),
            new Entry("/cc play [leave|leaderboard]", "选择自由游戏、离开游玩或查看榜单"),
            new Entry("/cc party invite|accept|leave|disband|info", "管理仅在本次相聚期间存在的小队"),
            new Entry("/cc daily leave|stats [游戏]", "退出自由匹配或查看独立游玩统计"),
            new Entry("/cc vote [游戏]", "打开投票菜单或直接投票"),
            new Entry("/cc spectate [leave | <游戏> <场地>]", "打开观战菜单或直接选择场地"),
            new Entry("/cc rank [teamboard|playerboard|info|recap]", "查看个人、队伍与结算积分")
    );

    private static final List<Entry> ADMIN_COMMANDS = List.of(
            new Entry("/cc switch [championship|daily]", "查看或切换正式赛事/自由游玩"),
            new Entry("/cc team", "打开管理员队伍与玩家管理界面"),
            new Entry("/cc team add|delete|info|list|tphere ...", "兼容指令：管理队伍、查看名单与队伍传送"),
            new Entry("/cc team member add|delete <队伍> <玩家>", "管理队伍成员"),
            new Entry("/cc game start <游戏> ...", "仅为指定队伍启动常规单局，不播报规则或自动调度观战者"),
            new Entry("/cc game stop <游戏> <场地> <实例> --confirm", "精确结束一个运行实例；已开赛则正常结算"),
            new Entry("/cc event start <游戏>", "开始常规正式比赛并管理规则、观战者和赛程；再次执行会紧急停止"),
            new Entry("/cc event teams import <cc-web链接> --confirm", "清空旧赛事积分与队伍并导入比赛就绪的赛事"),
            new Entry("/cc event export", "把当前赛事完整团队与个人逐游戏积分导出为 JSON"),
            new Entry("/cc event stop <游戏>", "显式停止正式比赛的赛程任务"),
            new Entry("/cc event reset|undo --confirm", "重置赛程或撤销最近正式比赛"),
            new Entry("/cc finale <游戏> start <场地> ...", "在指定场地启动已注册的冠军决赛"),
            new Entry("/cc finale dodgebolt pause|resume|restart-round|eliminate|force-win|stop ...", "躲避箭决赛裁判控制"),
            new Entry("/cc map edit <游戏>", "打开地图准备与编辑界面（可编辑未启用游戏）"),
            new Entry("/cc map blueprint create <名称> [覆盖星级]", "导出、自动归类并审查匹配赛建蓝图"),
            new Entry("/cc map blueprint audit <名称|all> [地图] [页码]", "审查蓝图难度和材料覆盖"),
            new Entry("/cc map blueprint preview <名称|all> [地图] [页码]", "预览单张或全库蓝图审查"),
            new Entry("/cc admin vote|world ...", "投票与世界管理"),
            new Entry("/cc admin reload|sudo|teleport|set-max-player ...", "系统维护与现场管理")
    );

    private CommandCatalog() {}

    public static void send(@NotNull CommandSender sender) {
        boolean admin = sender.hasPermission(MainCommand.ADMIN_PERMISSION);
        boolean player = admin || sender.hasPermission(MainCommand.PLAYER_PERMISSION);
        if (!player && !admin) {
            sender.sendMessage(MessageConfig.NO_PERMISSION);
            return;
        }

        StringBuilder message = new StringBuilder(MessageConfig.COMMAND_CATALOG_HEADER);
        if (player)
            appendSection(message, MessageConfig.COMMAND_CATALOG_PLAYER, PLAYER_COMMANDS);
        if (admin)
            appendSection(message, MessageConfig.COMMAND_CATALOG_ADMIN, ADMIN_COMMANDS);
        sender.sendMessage(message.toString());
    }

    private static void appendSection(@NotNull StringBuilder message, @NotNull String title,
                                      @NotNull List<Entry> entries) {
        message.append("\n").append(title);
        for (Entry entry : entries) {
            message.append("\n").append(MessageConfig.COMMAND_CATALOG_ROW
                    .replace("%usage%", entry.usage())
                    .replace("%description%", entry.description()));
        }
    }
}

package ink.ziip.championshipscore.command.game.stop;

import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Ends exactly one active runtime instance without stopping its game schedule or sibling copies. */
public final class GameStopSubCommand extends BaseSubCommand {
    public GameStopSubCommand() {
        super("stop", "精确结束一个场地实例（已开赛则正常结算）",
                "/cc game stop <游戏> <场地> <实例> --confirm");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 4 || !args[3].equalsIgnoreCase("--confirm")) {
            sendUsage(sender);
            return true;
        }

        GameTypeEnum game = GameTypeEnum.fromCommand(args[0]);
        if (game == null) {
            Utils.sendAdminError(sender, "未知游戏：&#fff566" + args[0]);
            return true;
        }

        List<BaseGameInstance> mapInstances = plugin.getGameManager().getStoppableMapInstances(game, args[1]);
        List<BaseGameInstance> tokenMatches = mapInstances.stream()
                .filter(instance -> plugin.getGameManager().getSpectatorInstanceToken(instance)
                        .equalsIgnoreCase(args[2]))
                .toList();
        if (tokenMatches.isEmpty()) {
            String available = mapInstances.stream()
                    .map(plugin.getGameManager()::getSpectatorInstanceToken)
                    .distinct().sorted().reduce((left, right) -> left + ", " + right).orElse("无");
            Utils.sendAdminError(sender, "找不到该场地的活动实例：&#fff566" + args[2]
                    + " &7| 可用实例：&f" + available);
            return true;
        }
        if (tokenMatches.size() > 1) {
            Utils.sendAdminError(sender, "实例标识不唯一，请使用命令补全重新选择；未结束任何实例。");
            return true;
        }
        BaseGameInstance target = tokenMatches.getFirst();

        String area = canonicalArea(target);
        String token = plugin.getGameManager().getSpectatorInstanceToken(target);
        String auditReason = "admin-game-stop:" + sender.getName();
        plugin.getLogger().info(Utils.formatGameLog(game, area, target.getGameStageEnum().name(),
                "管理员停止", "操作人=" + sender.getName() + " 实例=" + token));
        plugin.getGameManager().stopGameInstance(target, auditReason).whenComplete((result, failure) ->
                respondOnMain(sender, game, area, token, result, failure));
        return true;
    }

    private void respondOnMain(@NotNull CommandSender sender, @NotNull GameTypeEnum game,
                               @NotNull String area, @NotNull String token,
                               @Nullable GameManager.GameStopResult result, @Nullable Throwable failure) {
        Runnable response = () -> {
            String target = "&#fff566" + game.commandName() + " &7/ &f" + area + " &7/ &f" + token;
            if (failure != null || result == null) {
                Utils.sendAdminError(sender, "结束实例时发生异常：" + target);
                if (failure != null) plugin.getLogger().warning("game stop failed | " + failure.getMessage());
                return;
            }
            switch (result) {
                case SETTLEMENT_STARTED -> Utils.sendAdminSuccess(sender, "已按当前成绩正常结束并结算：" + target);
                case PRE_START_ABORTED -> Utils.sendAdminSuccess(sender,
                        "实例尚未正式开赛，已精确作废并重置（不产生积分）：" + target);
                case NOT_ACTIVE -> Utils.sendAdminInfo(sender, "该实例已结束或正在结算：" + target);
                case NOT_REGISTERED -> Utils.sendAdminError(sender, "该实例已被替换，请重新使用命令补全选择：" + target);
                case FAILED -> Utils.sendAdminError(sender, "实例未能安全结束，请查看服务端日志：" + target);
            }
        };
        if (Bukkit.isPrimaryThread()) response.run();
        else plugin.getServer().getScheduler().runTask(plugin, response);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> games = plugin.getGameManager().getStoppableInstances().stream()
                    .map(BaseGameInstance::getGameTypeEnum).distinct()
                    .map(GameTypeEnum::commandName).toList();
            return complete(games, args[0]);
        }
        GameTypeEnum game = GameTypeEnum.fromCommand(args[0]);
        if (game == null) return Collections.emptyList();
        if (args.length == 2) {
            List<String> areas = plugin.getGameManager().getStoppableInstances().stream()
                    .filter(instance -> instance.getGameTypeEnum() == game)
                    .map(GameStopSubCommand::canonicalArea)
                    .filter(Objects::nonNull).distinct().toList();
            return complete(areas, args[1]);
        }
        if (args.length == 3) {
            List<String> instances = plugin.getGameManager().getStoppableMapInstances(game, args[1]).stream()
                    .map(plugin.getGameManager()::getSpectatorInstanceToken).distinct().toList();
            return complete(instances, args[2]);
        }
        if (args.length == 4) return complete(List.of("--confirm"), args[3]);
        return Collections.emptyList();
    }

    private static @NotNull String canonicalArea(@NotNull BaseGameInstance instance) {
        String configName = instance.getGameConfig().getConfigName();
        if (configName != null && !configName.isBlank()) return configName;
        String areaName = instance.getGameConfig().getAreaName();
        return areaName == null || areaName.isBlank() ? "-" : areaName;
    }
}

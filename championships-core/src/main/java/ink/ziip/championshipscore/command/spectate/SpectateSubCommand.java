package ink.ziip.championshipscore.command.spectate;

import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SpectateSubCommand extends BaseSubCommand {
    public SpectateSubCommand() {
        super("spectate", "打开观战菜单或直接选择场地实例",
                "/cc spectate [leave | <游戏> <场地> [实例]]", PLAYER_PERMISSION);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Utils.sendAdminError(sender, "该命令只能由玩家执行");
            return true;
        }
        if (args.length > 3) {
            sendUsage(sender);
            return true;
        }
        if (args.length == 0) {
            if (plugin.getGameManager().canManuallySpectate(player)) {
                if (plugin.getGameManager().getSpectatorManager().isSpectatorLike(player.getUniqueId()))
                    plugin.getGameManager().openSpectatorControls(player);
                else plugin.getGameManager().openSpectateMenu(player);
            }
            return true;
        }
        if (args.length == 1) {
            if (!args[0].equalsIgnoreCase("leave")) {
                sendUsage(sender);
                return true;
            }
            if (plugin.getGameManager().leaveSpectating(player)) {
                sender.sendMessage(MessageConfig.SPECTATOR_LEAVING_AREA);
            } else {
                sender.sendMessage(MessageConfig.SPECTATOR_CANT_LEAVING_AREA);
            }
            return true;
        }

        if (!plugin.getGameManager().canManuallySpectate(player)) return true;

        if (args.length == 2 || args.length == 3) {
            GameTypeEnum gameTypeEnum = GameTypeEnum.fromCommand(args[0]);
            if (gameTypeEnum == null) {
                sendUsage(sender);
                return true;
            }
            if (!plugin.getGameManager().isGameEnabled(gameTypeEnum)) {
                Utils.sendAdminError(sender, "该游戏当前未启用");
                return true;
            }
            List<BaseGameInstance> candidates = plugin.getGameManager()
                    .getSpectatableMapInstances(player, gameTypeEnum, args[1]);
            BaseGameInstance baseArea = args.length == 2
                    ? candidates.stream().findFirst().orElse(null)
                    : candidates.stream()
                    .filter(instance -> plugin.getGameManager().getSpectatorInstanceToken(instance)
                            .equalsIgnoreCase(args[2]))
                    .findFirst().orElse(null);
            if (baseArea == null) {
                Utils.sendAdminError(sender, args.length == 3
                        ? "找不到该场地正在运行的实例：&#fff566" + args[2]
                        : "该场地尚未开启或已经结束，当前不可旁观");
                return true;
            }
            join(player, baseArea);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            // Game-name completion must not depend on live instances. In particular, remote Bingo
            // may have no synchronized match yet and must still appear as an enabled game option.
            return firstArgumentCompletions(plugin.getGameManager().getEnabledGames(), args[0]);
        }
        if (!(sender instanceof Player player)) return Collections.emptyList();
        if (args.length == 2) {
            GameTypeEnum gameTypeEnum = GameTypeEnum.fromCommand(args[0]);
            if (gameTypeEnum != null && plugin.getGameManager().isGameEnabled(gameTypeEnum)) {
                BaseGameInstanceManager<? extends BaseGameInstance> manager = plugin.getGameManager().getAreaManager(gameTypeEnum);
                if (manager != null) {
                    List<String> returnList = plugin.getGameManager().getSpectatableInstances(player).stream()
                            .filter(instance -> instance.getGameTypeEnum() == gameTypeEnum)
                            .map(instance -> instance.getGameConfig().getAreaName())
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .toList();
                    return filterStartsWith(returnList, args[1]);
                }
            }
        }
        if (args.length == 3) {
            GameTypeEnum gameTypeEnum = GameTypeEnum.fromCommand(args[0]);
            if (gameTypeEnum == null || !plugin.getGameManager().isGameEnabled(gameTypeEnum))
                return Collections.emptyList();
            List<String> instances = plugin.getGameManager().getSpectatableMapInstances(player, gameTypeEnum, args[1])
                    .stream().map(plugin.getGameManager()::getSpectatorInstanceToken).distinct().toList();
            return filterStartsWith(instances, args[2]);
        }
        return Collections.emptyList();
    }

    static @NotNull List<String> firstArgumentCompletions(@NotNull Set<GameTypeEnum> enabledGames,
                                                           @NotNull String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        if ("leave".startsWith(normalizedPrefix)) candidates.add("leave");
        enabledGames.stream()
                .map(GameTypeEnum::commandName)
                .filter(name -> name.startsWith(normalizedPrefix))
                .sorted(Comparator.naturalOrder())
                .forEach(candidates::add);
        candidates.sort(Comparator.naturalOrder());
        return List.copyOf(candidates);
    }

    private void join(@NotNull Player player, @NotNull BaseGameInstance target) {
        if (plugin.getGameManager().selectSpectatorArea(player, target)) {
            player.sendMessage(MessageConfig.SPECTATOR_JOIN_AREA
                    .replace("%game%", target.getGameTypeEnum().toString())
                    .replace("%area%", plugin.getGameManager().getSpectatorDisplayName(target)));
        } else {
            player.sendMessage(MessageConfig.SPECTATOR_CANT_JOIN_AREA);
        }
    }
}

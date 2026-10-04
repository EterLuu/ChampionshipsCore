package ink.ziip.championshipscore.command.game.start;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.start.GameStartArguments;
import ink.ziip.championshipscore.api.game.start.GameStartRules;
import ink.ziip.championshipscore.api.game.start.GameStartService;
import ink.ziip.championshipscore.api.game.start.SubArenaSupport;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.presentation.text.CoreMessages;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** Every game uses the same map, team and physical-arena selection surface. */
public final class GameStartMainCommand extends BaseSubCommand {
    public GameStartMainCommand() {
        super(
                "start",
                "指定地图与参赛队伍启动单局",
                "/cc game start <游戏> <地图|auto> [all|队伍...] [--arena all|编号]",
                ADMIN_PERMISSION);
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        try {
            var arguments = GameStartArguments.parse(args, false);
            var future = new GameStartService(plugin).startManual(arguments).toCompletableFuture();
            if (future.isDone()) {
                report(sender, future.join());
            } else {
                future.whenComplete(
                        (result, failure) -> {
                            if (!plugin.isEnabled()) return;
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (!plugin.isEnabled()) return;
                                                if (sender instanceof Player player
                                                        && Bukkit.getPlayer(player.getUniqueId())
                                                                != player) return;
                                                report(
                                                        sender,
                                                        failure == null
                                                                ? result
                                                                : new GameStartService.Result(
                                                                        false,
                                                                        "执行端启动失败："
                                                                                + failure
                                                                                        .getMessage()));
                                            });
                        });
            }
        } catch (IllegalArgumentException failure) {
            CoreMessages.sendAdminError(sender, failure.getMessage());
            sendUsage(sender);
        }
        return true;
    }

    private static void report(CommandSender sender, GameStartService.Result result) {
        if (result.started()) CoreMessages.sendAdminSuccess(sender, result.detail());
        else CoreMessages.sendAdminError(sender, result.detail());
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length < 1) return List.of();
        if (args.length == 1)
            return complete(
                    plugin.getGameManager().getEnabledGames().stream()
                            .map(GameTypeEnum::commandName)
                            .sorted()
                            .toList(),
                    args[0]);
        GameTypeEnum game = GameTypeEnum.fromCommand(args[0]);
        if (game == null || !plugin.getGameManager().isGameEnabled(game)) return List.of();
        var manager = plugin.getGameManager().getAreaManager(game);
        if (manager == null) return List.of();
        if (args.length == 2) {
            List<String> maps = new ArrayList<>(manager.getAreaNameList());
            maps.add("auto");
            return complete(maps, args[1]);
        }
        if (args[args.length - 2].equals("--arena")) {
            int count = 1;
            var map = manager.getArea(args[1]);
            if (map != null)
                count =
                        GameStartRules.internalArenas(game)
                                ? SubArenaSupport.count(map)
                                : manager.getMapInstances(args[1]).size();
            List<String> arenas =
                    new ArrayList<>(
                            IntStream.rangeClosed(1, count).mapToObj(Integer::toString).toList());
            arenas.add("all");
            return complete(arenas, args[args.length - 1]);
        }
        List<String> selectors = new ArrayList<>();
        if (args.length == 3) selectors.add("all");
        selectors.add("--arena");
        for (var team : plugin.getTeamManager().getTeamList()) {
            selectors.add(
                    team.getName().contains(" ") ? "\"" + team.getName() + "\"" : team.getName());
            selectors.add("#" + team.getId());
        }
        return complete(selectors, args[args.length - 1]);
    }
}

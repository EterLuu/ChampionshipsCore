package ink.ziip.championshipscore.command.map;

import ink.ziip.championshipscore.api.game.area.rename.MapRenameService;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/** /cc map rename <game> <old> <new> */
public final class MapRenameSubCommand extends BaseSubCommand {
    private final MapRenameService renameService;

    public MapRenameSubCommand() {
        super("rename", "安全改名地图并迁移数据库记录", "/cc map rename <游戏> <旧场地名> <新场地名>");
        renameService = new MapRenameService(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length != 3) {
            sendUsage(sender);
            return true;
        }
        GameTypeEnum game = parseGame(args[0]);
        if (game == null) {
            Utils.sendAdminError(sender, MessageConfig.MAP_EDITOR_COMMAND_UNKNOWN_GAME
                    .replace("%games%", String.join(", ", allGameNames())));
            return true;
        }
        if (!plugin.getGameManager().isGameManagerLoaded(game)) {
            Utils.sendAdminInfo(sender, MessageConfig.MAP_EDITOR_COMMAND_LOADING_FOR_RENAME);
            if (!plugin.getGameManager().loadGameForEditing(game)) {
                Utils.sendAdminError(sender, MessageConfig.MAP_EDITOR_COMMAND_MANAGER_LOAD_FAILED);
                return true;
            }
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> renameService.rename(sender, game, args[1], args[2]), 2L);
            return true;
        }
        renameService.rename(sender, game, args[1], args[2]);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return filterStartsWith(gameNames(), args[0]);
        if (args.length == 2) {
            GameTypeEnum game = parseGame(args[0]);
            return filterStartsWith(enabledAreaNames(game), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> gameNames() {
        return enabledGameNames();
    }

    private List<String> allGameNames() {
        return java.util.Arrays.stream(GameTypeEnum.values()).map(GameTypeEnum::commandName).toList();
    }

    private static @Nullable GameTypeEnum parseGame(@NotNull String raw) {
        return GameTypeEnum.fromCommand(raw);
    }
}

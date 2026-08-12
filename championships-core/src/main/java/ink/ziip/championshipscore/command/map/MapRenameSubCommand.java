package ink.ziip.championshipscore.command.map;

import ink.ziip.championshipscore.api.game.area.rename.MapRenameService;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
            Utils.sendAdminError(sender, "未知游戏，可用：&#fff566" + String.join(", ", gameNames()));
            return true;
        }
        if (!plugin.getGameManager().isGameManagerLoaded(game)) {
            Utils.sendAdminInfo(sender, "该游戏当前未启用，正在加载地图管理器……");
            if (!plugin.getGameManager().loadGameForEditing(game)) {
                Utils.sendAdminError(sender, "该游戏的地图管理器无法加载");
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
            BaseGameInstanceManager<?> manager = game == null ? null
                    : plugin.getGameManager().getAreaManager(game);
            return manager == null ? Collections.emptyList()
                    : filterStartsWith(manager.getAreaNameList(), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> gameNames() {
        List<String> names = new ArrayList<>();
        for (GameTypeEnum game : GameTypeEnum.values()) names.add(game.name());
        return names;
    }

    private static @Nullable GameTypeEnum parseGame(@NotNull String raw) {
        String normalized = raw.replace("_", "").replace("-", "");
        for (GameTypeEnum game : GameTypeEnum.values()) {
            if (game.name().replace("_", "").replace("-", "").equalsIgnoreCase(normalized)) return game;
        }
        return null;
    }
}

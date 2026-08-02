package ink.ziip.championshipscore.command.map;

import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.util.Utils;
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Opens the map preparation UI and loads a disabled game's manager on demand. */
public final class MapEditSubCommand extends BaseSubCommand {
    public MapEditSubCommand() {
        super("edit", "打开指定游戏的地图准备界面（包括未启用游戏）", "/cc map edit <游戏>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            sendUsage(sender);
            return true;
        }
        GameTypeEnum game = parseGame(args[0]);
        if (game == null) {
            Utils.sendAdminError(sender, "未知游戏，可用：&#fff566" + supportedNames());
            return true;
        }
        if (!plugin.getPrepareSessionManager().supports(game)) {
            Utils.sendAdminError(sender, "该游戏暂不支持地图准备，可用：&#fff566" + supportedNames());
            return true;
        }
        BaseGameInstanceManager<?> manager = plugin.getGameManager().getAreaManager(game);
        if (manager == null) {
            Utils.sendAdminError(sender, "该游戏没有地图管理器");
            return true;
        }
        if (plugin.getGameManager().isGameManagerLoaded(game)) {
            plugin.getPrepareSessionManager().openAreaListGui(player, game);
            return true;
        }

        Utils.sendAdminInfo(player, "该游戏当前未启用，正在仅为地图编辑加载其配置……");
        FoliaScheduler.global(plugin).runTask(() -> {
            boolean loaded = plugin.getGameManager().loadGameForEditing(game);
            FoliaScheduler.global(plugin).runEntityLater(player, () -> {
                if (!loaded) {
                    Utils.sendAdminError(sender, "该游戏的地图管理器无法加载");
                    return;
                }
                plugin.getPrepareSessionManager().openAreaListGui(player, game);
            }, 2L);
        });
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return filterStartsWith(supportedGames(), args[0]);
        return Collections.emptyList();
    }

    private List<String> supportedGames() {
        List<String> names = new ArrayList<>();
        for (GameTypeEnum game : GameTypeEnum.values()) {
            if (plugin.getPrepareSessionManager().supports(game)) names.add(game.name());
        }
        return names;
    }

    private String supportedNames() {
        return String.join(", ", supportedGames());
    }

    private static @Nullable GameTypeEnum parseGame(@NotNull String raw) {
        String normalized = raw.replace("_", "").replace("-", "");
        for (GameTypeEnum game : GameTypeEnum.values()) {
            if (game.name().replace("_", "").replace("-", "").equalsIgnoreCase(normalized)) {
                return game;
            }
        }
        return null;
    }
}

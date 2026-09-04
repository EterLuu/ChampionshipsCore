package ink.ziip.championshipscore.command.map;

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
            Utils.sendAdminError(sender, MessageConfig.MAP_EDITOR_COMMAND_UNKNOWN_GAME
                    .replace("%games%", supportedNames()));
            return true;
        }
        if (!plugin.getPrepareSessionManager().supports(game)) {
            Utils.sendAdminError(sender, MessageConfig.MAP_EDITOR_COMMAND_UNSUPPORTED_GAME
                    .replace("%games%", supportedNames()));
            return true;
        }
        BaseGameInstanceManager<?> manager = plugin.getGameManager().getAreaManager(game);
        if (manager == null) {
            Utils.sendAdminError(sender, MessageConfig.MAP_EDITOR_COMMAND_NO_MANAGER);
            return true;
        }
        if (plugin.getGameManager().isGameManagerLoaded(game)) {
            plugin.getPrepareSessionManager().openAreaListGui(player, game);
            return true;
        }

        Utils.sendAdminInfo(player, MessageConfig.MAP_EDITOR_COMMAND_LOADING_DISABLED);
        if (!plugin.getGameManager().loadGameForEditing(game)) {
            Utils.sendAdminError(sender, MessageConfig.MAP_EDITOR_COMMAND_MANAGER_LOAD_FAILED);
            return true;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.getPrepareSessionManager().openAreaListGui(player, game), 2L);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return filterStartsWith(supportedGames(true), args[0]);
        return Collections.emptyList();
    }

    private List<String> supportedGames(boolean enabledOnly) {
        List<String> names = new ArrayList<>();
        for (GameTypeEnum game : GameTypeEnum.values()) {
            if ((!enabledOnly || plugin.getGameManager().isGameEnabled(game))
                    && plugin.getPrepareSessionManager().supports(game)) names.add(game.commandName());
        }
        return names;
    }

    private String supportedNames() {
        return String.join(", ", supportedGames(false));
    }

    private static @Nullable GameTypeEnum parseGame(@NotNull String raw) {
        return GameTypeEnum.fromCommand(raw);
    }
}

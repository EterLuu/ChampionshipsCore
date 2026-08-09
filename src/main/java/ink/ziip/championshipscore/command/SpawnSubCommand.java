package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class SpawnSubCommand extends BaseSubCommand {
    public SpawnSubCommand() {
        super("spawn", "回到大厅（游戏或观战期间不可用）", "/cc spawn", PLAYER_PERMISSION);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 0) {
            sendUsage(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            Utils.sendAdminError(sender, "该命令只能由玩家执行");
            return true;
        }
        if (plugin.getGameManager().getBasePlayerArea(player.getUniqueId()) != null
                || plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId()) != null) {
            player.sendMessage(Utils.translateColorCodes(
                    "&#bababa[&#ff6b26大厅&#bababa] &#ededed正在游戏或观战，不能返回大厅"));
            return true;
        }
        Location lobby = CCConfig.LOBBY_LOCATION;
        if (lobby == null || lobby.getWorld() == null) {
            Utils.sendAdminError(sender, "大厅出生点尚未配置");
            return true;
        }
        player.teleport(lobby);

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

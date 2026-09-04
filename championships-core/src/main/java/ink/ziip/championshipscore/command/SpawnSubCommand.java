package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
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
            Utils.sendAdminError(sender, MessageConfig.COMMAND_PLAYER_ONLY);
            return true;
        }
        if (plugin.getGameManager().getBasePlayerArea(player.getUniqueId()) != null
                || plugin.getGameManager().getPlayerSpectatorStatus(player.getUniqueId()) != null) {
            player.sendMessage(Utils.translateColorCodes(MessageConfig.SPAWN_IN_GAME_OR_SPECTATING));
            return true;
        }
        Location lobby = CCConfig.LOBBY_LOCATION;
        if (lobby == null || lobby.getWorld() == null) {
            Utils.sendAdminError(sender, MessageConfig.SPAWN_MISSING);
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

package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.configuration.config.CCConfig;
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
        super("spawn");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player) {
            Location location = player.getLocation();
            if (location.getWorld() != null && CCConfig.LOBBY_LOCATION.getWorld() != null && location.getWorld().getName().equals(CCConfig.LOBBY_LOCATION.getWorld().getName())) {
                player.teleport(CCConfig.LOBBY_LOCATION);
            }

        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}

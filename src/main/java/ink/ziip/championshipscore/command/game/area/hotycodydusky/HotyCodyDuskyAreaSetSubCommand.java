package ink.ziip.championshipscore.command.game.area.hotycodydusky;

import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyConfig;
import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyTeamArea;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HotyCodyDuskyAreaSetSubCommand extends BaseSubCommand {
    private final String[] arguments = {
            "name",
            "timer",
            "area-pos",
            "spectator-spawn-point",
            "player-spawn-point",
    };

    public HotyCodyDuskyAreaSetSubCommand() {
        super("set");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = (Player) sender;
        HotyCodyDuskyTeamArea hotyCodyDuskyTeamArea = plugin.getGameManager().getHotyCodyDuskyManager().getArea(args[0]);
        if (hotyCodyDuskyTeamArea == null) {
            String message = MessageConfig.AREA_SETTING_OPTION_FAILED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }
        HotyCodyDuskyConfig hotyCodyDuskyConfig = hotyCodyDuskyTeamArea.getGameConfig();
        if (args.length == 2) {
            if (args[1].equals("spectator-spawn-point")) {
                hotyCodyDuskyConfig.setSpectatorSpawnPoint(player.getLocation());
            }
            if (args[1].equals("area-pos")) {
                Vector[] vectors = plugin.getWorldEditManager().getPlayerSelection(player, true);
                hotyCodyDuskyConfig.setAreaPos1(vectors[0]);
                hotyCodyDuskyConfig.setAreaPos2(vectors[1]);
            }
            if (args[1].equals("player-spawn-point")) {
                hotyCodyDuskyConfig.setPlayerSpawnPoint(player.getLocation());
            }
            hotyCodyDuskyConfig.saveOptions();
            String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getGameManager().getHotyCodyDuskyManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }

        if (args.length == 2) {
            List<String> returnList = new ArrayList<>(Arrays.asList(arguments));
            returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
            return returnList;
        }

        return Collections.emptyList();
    }
}

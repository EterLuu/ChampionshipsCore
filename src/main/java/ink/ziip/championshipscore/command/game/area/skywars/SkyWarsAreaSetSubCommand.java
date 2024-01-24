package ink.ziip.championshipscore.command.game.area.skywars;

import ink.ziip.championshipscore.api.game.skywars.SkyWarsTeamArea;
import ink.ziip.championshipscore.api.game.skywars.SkyWarsConfig;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
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

public class SkyWarsAreaSetSubCommand extends BaseSubCommand {
    private final String[] arguments = {
            "name",
            "timer",
            "area-pos",
            "pre-spawn-point",
            "spectator-spawn-point",
            "team-spawn-points",
    };

    public SkyWarsAreaSetSubCommand() {
        super("set");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = (Player) sender;
        SkyWarsTeamArea skyWarsArea = plugin.getGameManager().getSkyWarsManager().getArea(args[0]);
        if (skyWarsArea == null) {
            String message = MessageConfig.AREA_SETTING_OPTION_FAILED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }
        SkyWarsConfig skyWarsConfig = skyWarsArea.getGameConfig();
        if (args.length == 2) {
            if (args[1].equals("pre-spawn-point")) {
                skyWarsConfig.setPreSpawnPoint(player.getLocation());
            }
            if (args[1].equals("spectator-spawn-point")) {
                skyWarsConfig.setSpectatorSpawnPoint(player.getLocation());
            }
            if (args[1].equals("area-pos")) {
                Vector[] vectors = plugin.getWorldEditManager().getPlayerSelection(player);
                skyWarsConfig.setAreaPos1(vectors[0]);
                skyWarsConfig.setAreaPos2(vectors[1]);
            }
            skyWarsConfig.saveOptions();
            String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }

        if (args.length == 3) {
            if (args[1].equals("team-spawn-points")) {
                if (skyWarsConfig.getTeamSpawnPoints() == null) {
                    skyWarsConfig.setTeamSpawnPoints(new ArrayList<>());
                }
                if (args[2].equals("add")) {
                    skyWarsConfig.getTeamSpawnPoints().add(Utils.getLocationConfigString(player.getLocation()));
                }
                if (args[2].equals("clean")) {
                    skyWarsConfig.getTeamSpawnPoints().clear();
                }
                skyWarsConfig.saveOptions();
                String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                        .replace("%area%", args[0])
                        .replace("%option%", args[1]);
                sender.sendMessage(message);
                return true;
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getGameManager().getSkyWarsManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }

        if (args.length == 2) {
            List<String> returnList = new ArrayList<>(Arrays.asList(arguments));
            returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
            return returnList;
        }

        if (args.length == 3) {
            if (args[1].contains("team-spawn-points")) {
                return Arrays.asList("add", "clean");
            }
        }

        return Collections.emptyList();
    }
}

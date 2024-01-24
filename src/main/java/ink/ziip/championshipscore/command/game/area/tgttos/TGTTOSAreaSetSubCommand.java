package ink.ziip.championshipscore.command.game.area.tgttos;

import ink.ziip.championshipscore.api.game.tgttos.TGTTOSConfig;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSTeamArea;
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

public class TGTTOSAreaSetSubCommand extends BaseSubCommand {
    private final String[] arguments = {
            "name",
            "timer",
            "area-type",
            "spectator-spawn-point",
            "area-pos",
            "monster-spawn-points",
            "chicken-spawn-points",
            "player-spawn-points",
    };

    public TGTTOSAreaSetSubCommand() {
        super("set");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = (Player) sender;
        TGTTOSTeamArea tgttosTeamArea = plugin.getGameManager().getTgttosManager().getArea(args[0]);
        if (tgttosTeamArea == null) {
            String message = MessageConfig.AREA_SETTING_OPTION_FAILED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }
        TGTTOSConfig tgttosConfig = tgttosTeamArea.getTgttosConfig();
        if (args.length == 2) {
            if (args[1].equals("spectator-spawn-point")) {
                tgttosConfig.setSpectatorSpawnPoint(player.getLocation());
            }
            if (args[1].equals("area-pos")) {
                Vector[] vectors = plugin.getWorldEditManager().getPlayerSelection(player);
                tgttosConfig.setAreaPos1(vectors[0]);
                tgttosConfig.setAreaPos2(vectors[1]);
            }
            tgttosConfig.saveOptions();
            String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }

        if (args.length == 3) {
            if (args[1].equals("monster-spawn-points")) {
                if (tgttosConfig.getMonsterSpawnPoints() == null) {
                    tgttosConfig.setMonsterSpawnPoints(new ArrayList<>());
                }
                if (args[2].equals("add")) {
                    tgttosConfig.getMonsterSpawnPoints().add(Utils.getLocationConfigString(player.getLocation()));
                }
                if (args[2].equals("clean")) {
                    tgttosConfig.getMonsterSpawnPoints().clear();
                }
                tgttosConfig.saveOptions();
                String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                        .replace("%area%", args[0])
                        .replace("%option%", args[1]);
                sender.sendMessage(message);
                return true;
            }
            if (args[1].equals("chicken-spawn-points")) {
                if (tgttosConfig.getChickenSpawnPoints() == null) {
                    tgttosConfig.setChickenSpawnPoints(new ArrayList<>());
                }
                if (args[2].equals("add")) {
                    tgttosConfig.getChickenSpawnPoints().add(Utils.getLocationConfigString(player.getLocation()));
                }
                if (args[2].equals("clean")) {
                    tgttosConfig.getChickenSpawnPoints().clear();
                }
                tgttosConfig.saveOptions();
                String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                        .replace("%area%", args[0])
                        .replace("%option%", args[1]);
                sender.sendMessage(message);
                return true;
            }
            if (args[1].equals("player-spawn-points")) {
                if (tgttosConfig.getPlayerSpawnPoints() == null) {
                    tgttosConfig.setPlayerSpawnPoints(new ArrayList<>());
                }
                if (args[2].equals("add")) {
                    tgttosConfig.getPlayerSpawnPoints().add(Utils.getLocationConfigString(player.getLocation()));
                }
                if (args[2].equals("clean")) {
                    tgttosConfig.getPlayerSpawnPoints().clear();
                }
                tgttosConfig.saveOptions();
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
            List<String> returnList = plugin.getGameManager().getTgttosManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }

        if (args.length == 2) {
            List<String> returnList = new ArrayList<>(Arrays.asList(arguments));
            returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
            return returnList;
        }

        if (args.length == 3) {
            if (args[1].contains("spawn-points")) {
                return Arrays.asList("add", "clean");
            }
        }

        return Collections.emptyList();
    }
}

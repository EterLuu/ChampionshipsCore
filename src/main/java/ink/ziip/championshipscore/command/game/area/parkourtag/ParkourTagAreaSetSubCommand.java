package ink.ziip.championshipscore.command.game.area.parkourtag;

import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagArea;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagConfig;
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

public class ParkourTagAreaSetSubCommand extends BaseSubCommand {
    private final String[] arguments = {
            "name",
            "timer",
            "area-pos",
            "right-pre-spawn-point",
            "left-pre-spawn-point",
            "spectator-spawn-point",
            "left-area-area-pos",
            "left-area-chaser-spawn-point",
            "left-area-escapee-spawn-points",
            "right-area-area-pos",
            "right-area-chaser-spawn-point",
            "right-area-escapee-spawn-points",
    };

    public ParkourTagAreaSetSubCommand() {
        super("set");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = (Player) sender;
        ParkourTagArea parkourTagArea = plugin.getGameManager().getParkourTagManager().getArea(args[0]);
        if (parkourTagArea == null) {
            String message = MessageConfig.AREA_SETTING_OPTION_FAILED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }
        ParkourTagConfig parkourTagConfig = parkourTagArea.getGameConfig();
        if (args.length == 2) {
            if (args[1].equals("right-pre-spawn-point")) {
                parkourTagConfig.setRightPreSpawnPoint(player.getLocation());
            }
            if (args[1].equals("left-pre-spawn-point")) {
                parkourTagConfig.setLeftPreSpawnPoint(player.getLocation());
            }
            if (args[1].equals("spectator-spawn-point")) {
                parkourTagConfig.setSpectatorSpawnPoint(player.getLocation());
            }
            if (args[1].equals("area-pos")) {
                Vector[] vectors = plugin.getWorldEditManager().getPlayerSelection(player, true);
                parkourTagConfig.setAreaPos1(vectors[0]);
                parkourTagConfig.setAreaPos2(vectors[1]);
            }
            if (args[1].equals("right-area-area-pos")) {
                Vector[] vectors = plugin.getWorldEditManager().getPlayerSelection(player, true);
                parkourTagConfig.setRightAreaAreaPos1(vectors[0]);
                parkourTagConfig.setRightAreaAreaPos2(vectors[1]);
            }
            if (args[1].equals("left-area-area-pos")) {
                Vector[] vectors = plugin.getWorldEditManager().getPlayerSelection(player, true);
                parkourTagConfig.setLeftAreaAreaPos1(vectors[0]);
                parkourTagConfig.setLeftAreaAreaPos2(vectors[1]);
            }
            if (args[1].equals("right-area-chaser-spawn-point")) {
                parkourTagConfig.setRightAreaChaserSpawnPoint(player.getLocation());
            }
            if (args[1].equals("left-area-chaser-spawn-point")) {
                parkourTagConfig.setLeftAreaChaserSpawnPoint(player.getLocation());
            }
            parkourTagConfig.saveOptions();
            String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }

        if (args.length == 3) {
            if (args[1].equals("right-area-escapee-spawn-points")) {
                if (parkourTagConfig.getRightAreaEscapeeSpawnPoints() == null) {
                    parkourTagConfig.setRightAreaEscapeeSpawnPoints(new ArrayList<>());
                }
                if (args[2].equals("add")) {
                    parkourTagConfig.getRightAreaEscapeeSpawnPoints().add(Utils.getLocationConfigString(player.getLocation()));
                }
                if (args[2].equals("clean")) {
                    parkourTagConfig.getRightAreaEscapeeSpawnPoints().clear();
                }
                parkourTagConfig.saveOptions();
                String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                        .replace("%area%", args[0])
                        .replace("%option%", args[1]);
                sender.sendMessage(message);
                return true;
            }
            if (args[1].equals("left-area-escapee-spawn-points")) {
                if (parkourTagConfig.getLeftAreaEscapeeSpawnPoints() == null) {
                    parkourTagConfig.setLeftAreaEscapeeSpawnPoints(new ArrayList<>());
                }
                if (args[2].equals("add")) {
                    parkourTagConfig.getLeftAreaEscapeeSpawnPoints().add(Utils.getLocationConfigString(player.getLocation()));
                }
                if (args[2].equals("clean")) {
                    parkourTagConfig.getLeftAreaEscapeeSpawnPoints().clear();
                }
                parkourTagConfig.saveOptions();
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
            List<String> returnList = plugin.getGameManager().getParkourTagManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }

        if (args.length == 2) {
            List<String> returnList = new ArrayList<>(Arrays.asList(arguments));
            returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
            return returnList;
        }

        if (args.length == 3) {
            if (args[1].contains("area-escapee-spawn-points")) {
                return Arrays.asList("add", "clean");
            }
        }

        return Collections.emptyList();
    }
}

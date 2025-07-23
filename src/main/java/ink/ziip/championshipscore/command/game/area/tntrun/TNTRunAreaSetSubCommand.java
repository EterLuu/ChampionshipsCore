package ink.ziip.championshipscore.command.game.area.tntrun;

import ink.ziip.championshipscore.api.game.tntrun.TNTRunConfig;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunTeamArea;
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

public class TNTRunAreaSetSubCommand extends BaseSubCommand {
    private final String[] arguments = {
            "name",
            "timer",
            "area-pos",
            "spectator-spawn-point",
            "player-spawn-points",
    };

    public TNTRunAreaSetSubCommand() {
        super("set");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = (Player) sender;
        TNTRunTeamArea tntRunTeamArea = plugin.getGameManager().getTntRunManager().getArea(args[0]);
        if (tntRunTeamArea == null) {
            String message = MessageConfig.AREA_SETTING_OPTION_FAILED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }
        TNTRunConfig tntRunConfig = tntRunTeamArea.getGameConfig();
        if (args.length == 2) {
            if (args[1].equals("spectator-spawn-point")) {
                tntRunConfig.setSpectatorSpawnPoint(player.getLocation());
            }
            if (args[1].equals("area-pos")) {
                Vector[] vectors = plugin.getWorldEditManager().getPlayerSelection(player, true);
                tntRunConfig.setAreaPos1(vectors[0]);
                tntRunConfig.setAreaPos2(vectors[1]);
            }
            tntRunConfig.saveOptions();
            String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }

        if (args.length == 3) {
            if (args[1].equals("player-spawn-points")) {
                if (tntRunConfig.getPlayerSpawnPoints() == null) {
                    tntRunConfig.setPlayerSpawnPoints(new ArrayList<>());
                }
                if (args[2].equals("add")) {
                    tntRunConfig.getPlayerSpawnPoints().add(Utils.getLocationConfigString(player.getLocation()));
                }
                if (args[2].equals("clean")) {
                    tntRunConfig.getPlayerSpawnPoints().clear();
                }
                tntRunConfig.saveOptions();
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
            List<String> returnList = plugin.getGameManager().getTntRunManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }

        if (args.length == 2) {
            List<String> returnList = new ArrayList<>(Arrays.asList(arguments));
            returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
            return returnList;
        }

        if (args.length == 3) {
            if (args[1].contains("player-spawn-points")) {
                return Arrays.asList("add", "clean");
            }
        }

        return Collections.emptyList();
    }
}

package ink.ziip.championshipscore.command.game.area.decarnival;

import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalArea;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalConfig;
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

public class DragonEggCarnivalAreaSetSubCommand extends BaseSubCommand {
    private final String[] arguments = {
            "name",
            "area-pos",
            "spectator-spawn-point",
            "left-spawn-point",
            "right-spawn-point",
            "dragon-egg-spawn-point",
            "dragon-spawn-point",
            "kits",
    };

    public DragonEggCarnivalAreaSetSubCommand() {
        super("set");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = (Player) sender;
        DragonEggCarnivalArea dragonEggCarnivalArea = plugin.getGameManager().getDragonEggCarnivalManager().getArea(args[0]);
        if (dragonEggCarnivalArea == null) {
            String message = MessageConfig.AREA_SETTING_OPTION_FAILED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }
        DragonEggCarnivalConfig dragonEggCarnivalConfig = dragonEggCarnivalArea.getGameConfig();
        if (args.length == 2) {
            if (args[1].equals("spectator-spawn-point")) {
                dragonEggCarnivalConfig.setSpectatorSpawnPoint(player.getLocation());
            }
            if (args[1].equals("area-pos")) {
                Vector[] vectors = plugin.getWorldEditManager().getPlayerSelection(player);
                dragonEggCarnivalConfig.setAreaPos1(vectors[0]);
                dragonEggCarnivalConfig.setAreaPos2(vectors[1]);
            }
            if (args[1].equals("right-spawn-point")) {
                dragonEggCarnivalConfig.setRightSpawnPoint(player.getLocation());
            }
            if (args[1].equals("left-spawn-point")) {
                dragonEggCarnivalConfig.setLeftSpawnPoint(player.getLocation());
            }
            if (args[1].equals("dragon-egg-spawn-point")) {
                dragonEggCarnivalConfig.setDragonEggSpawnPoint(player.getLocation());
            }
            if (args[1].equals("dragon-spawn-point")) {
                dragonEggCarnivalConfig.setDragonSpawnPoint(player.getLocation());
            }
            dragonEggCarnivalConfig.saveOptions();
            String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }
        if (args.length == 3) {
            if (args[1].equals("kits")) {
                if (dragonEggCarnivalConfig.getKits() == null) {
                    dragonEggCarnivalConfig.setKits(new ArrayList<>());
                }
                if (args[2].equals("add")) {
                    dragonEggCarnivalConfig.getKits().add(player.getInventory().getItemInMainHand());
                }
                if (args[2].equals("clean")) {
                    dragonEggCarnivalConfig.getKits().clear();
                }
                dragonEggCarnivalConfig.saveOptions();
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
            List<String> returnList = plugin.getGameManager().getDragonEggCarnivalManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }

        if (args.length == 2) {
            List<String> returnList = new ArrayList<>(Arrays.asList(arguments));
            returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
            return returnList;
        }

        if (args.length == 3) {
            if (args[1].contains("kits")) {
                return Arrays.asList("add", "clean");
            }
        }

        return Collections.emptyList();
    }
}

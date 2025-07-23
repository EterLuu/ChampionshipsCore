package ink.ziip.championshipscore.command.game.area.battlebox;

import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxConfig;
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

public class BattleBoxAreaSetSubCommand extends BaseSubCommand {
    private final String[] arguments = {
            "name",
            "timer",
            "right-spawn-point",
            "left-spawn-point",
            "right-pre-spawn-point",
            "left-pre-spawn-point",
            "wool-pos",
            "area-pos",
            "potion-spawn-points",
            "spectator-spawn-point",
    };

    public BattleBoxAreaSetSubCommand() {
        super("set");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = (Player) sender;
        BattleBoxArea battleBoxArea = plugin.getGameManager().getBattleBoxManager().getArea(args[0]);
        if (battleBoxArea == null) {
            String message = MessageConfig.AREA_SETTING_OPTION_FAILED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }
        BattleBoxConfig battleBoxConfig = battleBoxArea.getGameConfig();
        if (args.length == 2) {
            if (args[1].equals("right-spawn-point")) {
                battleBoxConfig.setRightSpawnPoint(player.getLocation());
            }
            if (args[1].equals("left-spawn-point")) {
                battleBoxConfig.setLeftSpawnPoint(player.getLocation());
            }
            if (args[1].equals("right-pre-spawn-point")) {
                battleBoxConfig.setRightPreSpawnPoint(player.getLocation());
            }
            if (args[1].equals("left-pre-spawn-point")) {
                battleBoxConfig.setLeftPreSpawnPoint(player.getLocation());
            }
            if (args[1].equals("spectator-spawn-point")) {
                battleBoxConfig.setSpectatorSpawnPoint(player.getLocation());
            }
            if (args[1].equals("wool-pos")) {
                Vector[] vectors = plugin.getWorldEditManager().getPlayerSelection(player, true);
                battleBoxConfig.setWoolPos1(vectors[0]);
                battleBoxConfig.setWoolPos2(vectors[1]);
            }
            if (args[1].equals("area-pos")) {
                Vector[] vectors = plugin.getWorldEditManager().getPlayerSelection(player, true);
                battleBoxConfig.setAreaPos1(vectors[0]);
                battleBoxConfig.setAreaPos2(vectors[1]);
            }
            battleBoxConfig.saveOptions();
            String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }

        if (args.length == 3) {
            if (args[1].equals("potion-spawn-points")) {
                if (battleBoxConfig.getPotionSpawnPoints() == null) {
                    battleBoxConfig.setPotionSpawnPoints(new ArrayList<>());
                }
                if (args[2].equals("add")) {
                    battleBoxConfig.getPotionSpawnPoints().add(Utils.getLocationConfigString(player.getLocation()));
                }
                if (args[2].equals("clean")) {
                    battleBoxConfig.getPotionSpawnPoints().clear();
                }
                battleBoxConfig.saveOptions();
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
            List<String> returnList = plugin.getGameManager().getBattleBoxManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }

        if (args.length == 2) {
            List<String> returnList = new ArrayList<>(Arrays.asList(arguments));
            returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
            return returnList;
        }

        if (args.length == 3) {
            if (args[1].equals("potion-spawn-points")) {
                return Arrays.asList("add", "clean");
            }
        }

        return Collections.emptyList();
    }
}

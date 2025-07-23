package ink.ziip.championshipscore.command.game.area.snowball;

import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownConfig;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownTeamArea;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SnowBallAreaSetSubCommand extends BaseSubCommand {
    private final String[] arguments = {
            "name",
            "timer",
            "area-pos",
            "spectator-spawn-point",
            "player-spawn-points",
    };

    public SnowBallAreaSetSubCommand() {
        super("set");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = (Player) sender;
        SnowballShowdownTeamArea snowballShowdownTeamArea = plugin.getGameManager().getSnowballShowdownManager().getArea(args[0]);
        if (snowballShowdownTeamArea == null) {
            String message = MessageConfig.AREA_SETTING_OPTION_FAILED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }
        SnowballShowdownConfig snowballShowdownConfig = snowballShowdownTeamArea.getGameConfig();
        if (args.length == 2) {
            if (args[1].equals("spectator-spawn-point")) {
                snowballShowdownConfig.setSpectatorSpawnPoint(player.getLocation());
            }
            if (args[1].equals("area-pos")) {
                Vector[] vectors = plugin.getWorldEditManager().getPlayerSelection(player, true);
                snowballShowdownConfig.setAreaPos1(vectors[0]);
                snowballShowdownConfig.setAreaPos2(vectors[1]);
            }
            snowballShowdownConfig.saveOptions();
            String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }

        if (args.length == 4) {
            if (args[1].equals("player-spawn-points")) {
                if (snowballShowdownConfig.getPlayerSpawnPoints() == null) {
                    return true;
                }
                ConfigurationSection configurationSection = snowballShowdownConfig.getPlayerSpawnPoints();
                if (configurationSection.contains(args[2])) {
                    List<String> locations = configurationSection.getStringList(args[2]);
                    if (args[3].equals("add")) {
                        locations.add(Utils.getLocationConfigString(player.getLocation()));
                        configurationSection.set(args[2], locations);
                    }
                    if (args[3].equals("clean")) {
                        configurationSection.set(args[2], new ArrayList<String>());
                    }
                }
                snowballShowdownConfig.saveOptions();
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
            if (args[1].equals("player-spawn-points")) {
                SnowballShowdownTeamArea snowballShowdownTeamArea = plugin.getGameManager().getSnowballShowdownManager().getArea(args[0]);
                if (snowballShowdownTeamArea == null)
                    return Collections.emptyList();

                SnowballShowdownConfig snowballShowdownConfig = snowballShowdownTeamArea.getGameConfig();
                ConfigurationSection configurationSection = snowballShowdownConfig.getPlayerSpawnPoints();

                if (configurationSection == null)
                    return Collections.emptyList();

                return new ArrayList<>(configurationSection.getKeys(false));
            }
        }

        if (args.length == 4) {
            if (args[1].equals("player-spawn-points")) {
                return Arrays.asList("add", "clean");
            }
        }

        return Collections.emptyList();
    }
}

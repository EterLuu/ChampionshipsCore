package ink.ziip.championshipscore.command.game.area.parkourwarrior;

import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorConfig;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorTeamArea;
import ink.ziip.championshipscore.api.object.game.parkourwarrior.PKWCheckPointTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
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

public class ParkourWarriorAreaSetSubCommand extends BaseSubCommand {
    private final String[] arguments = {
            "name",
            "player-spawn-point",
            "spectator-spawn-point",
            "area-pos",
            "checkpoints"
    };

    private final String[] checkpointArguments = {
            "add",
            "set-spawn",
            "set-enter",
            "add-sub-checkpoint"
    };

    public ParkourWarriorAreaSetSubCommand() {
        super("set");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Player player = (Player) sender;
        ParkourWarriorTeamArea parkourWarriorTeamArea = plugin.getGameManager().getParkourWarriorManager().getArea(args[0]);
        if (parkourWarriorTeamArea == null) {
            String message = MessageConfig.AREA_SETTING_OPTION_FAILED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }
        ParkourWarriorConfig parkourWarriorConfig = parkourWarriorTeamArea.getGameConfig();
        if (args.length == 2) {
            if (args[1].equals("spectator-spawn-point")) {
                parkourWarriorConfig.setSpectatorSpawnPoint(player.getLocation());
            }
            if (args[1].equals("area-pos")) {
                Vector[] vectors = plugin.getWorldEditManager().getPlayerSelection(player, true);
                parkourWarriorConfig.setAreaPos1(vectors[0]);
                parkourWarriorConfig.setAreaPos2(vectors[1]);
            }
            if (args[1].equals("player-spawn-point")) {
                parkourWarriorConfig.setPlayerSpawnPoint(player.getLocation());
            }
            parkourWarriorConfig.saveOptions();
            String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]);
            sender.sendMessage(message);
            return true;
        }

        if (args.length == 4) {
            if (args[1].equals("checkpoints")) {
                ConfigurationSection pkwCheckpoint = parkourWarriorConfig.getCheckpoints().getConfigurationSection(args[3]);
                if (pkwCheckpoint == null) {
                    return true;
                }
                if (args[2].equals("set-spawn")) {
                    pkwCheckpoint.set("spawn", player.getLocation());
                    String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                            .replace("%area%", args[0])
                            .replace("%option%", args[2]);
                    sender.sendMessage(message);
                }
                if (args[2].equals("set-enter")) {
                    Vector[] vectors = plugin.getWorldEditManager().getPlayerSelection(player, false);
                    pkwCheckpoint.set("enter.pos1", vectors[0]);
                    pkwCheckpoint.set("enter.pos2", vectors[1]);
                    String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                            .replace("%area%", args[0])
                            .replace("%option%", args[2]);
                    sender.sendMessage(message);
                }
                if (args[2].equals("add-sub-checkpoint")) {
                    if (pkwCheckpoint.getConfigurationSection("sub-checkpoints") == null) {
                        ConfigurationSection pkwSubCheckpoint = pkwCheckpoint.createSection("sub-checkpoints");
                        ConfigurationSection pkwSubCheckpointVector = pkwSubCheckpoint.createSection("1");
                        Vector[] vectors = plugin.getWorldEditManager().getPlayerSelection(player, false);
                        pkwSubCheckpointVector.set("pos1", vectors[0]);
                        pkwSubCheckpointVector.set("pos2", vectors[1]);
                        String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                                .replace("%area%", args[0])
                                .replace("%option%", args[2]);
                        sender.sendMessage(message);
                    } else {
                        int subCheckpointIndex = pkwCheckpoint.getConfigurationSection("sub-checkpoints").getKeys(false).size() + 1;
                        ConfigurationSection pkwSubCheckpoint = pkwCheckpoint.getConfigurationSection("sub-checkpoints");
                        ConfigurationSection pkwSubCheckpointVector = pkwSubCheckpoint.createSection(subCheckpointIndex + "");
                        Vector[] vectors = plugin.getWorldEditManager().getPlayerSelection(player, false);
                        pkwSubCheckpointVector.set("pos1", vectors[0]);
                        pkwSubCheckpointVector.set("pos2", vectors[1]);
                        String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                                .replace("%area%", args[0])
                                .replace("%option%", args[2]);
                        sender.sendMessage(message);
                    }
                }
                parkourWarriorConfig.saveOptions();

                // Dangerous operation, but necessary to reload checkpoints
                parkourWarriorTeamArea.loadCheckpoints();

                return true;
            }
        }

        if (args.length == 5) {
            if (args[1].equals("checkpoints")) {
                ConfigurationSection pkwCheckpoint = parkourWarriorConfig.getCheckpoints().getConfigurationSection(args[3]);
                if (pkwCheckpoint == null) {
                    if (args[2].equals("add")) {
                        String name = args[3];
                        PKWCheckPointTypeEnum pkwCheckPointType = PKWCheckPointTypeEnum.valueOf(args[4]);
                        pkwCheckpoint = parkourWarriorConfig.getCheckpoints().createSection(name);
                        pkwCheckpoint.set("name", name);
                        pkwCheckpoint.set("type", pkwCheckPointType.toString());
                        pkwCheckpoint.set("spawn", player.getLocation());
                        String message = MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                                .replace("%area%", args[0])
                                .replace("%option%", args[2]);
                        sender.sendMessage(message);
                    }
                    parkourWarriorConfig.saveOptions();
                    return true;
                }
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getGameManager().getParkourWarriorManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }

        if (args.length == 2) {
            List<String> returnList = new ArrayList<>(Arrays.asList(arguments));
            returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
            return returnList;
        }

        if (args.length == 3) {
            List<String> returnList = new ArrayList<>(Arrays.asList(checkpointArguments));
            returnList.removeIf(s -> s != null && !s.startsWith(args[2]));
            return returnList;
        }

        if (args.length == 4) {
            if (args[2].equals("set-spawn") || args[2].equals("set-enter") || args[2].equals("add-sub-checkpoint")) {
                ParkourWarriorTeamArea parkourWarriorTeamArea = plugin.getGameManager().getParkourWarriorManager().getArea(args[0]);
                if (parkourWarriorTeamArea == null) {
                    return Collections.emptyList();
                }
                List<String> returnList = new ArrayList<>(parkourWarriorTeamArea.getGameConfig().getCheckpoints().getKeys(false).stream().toList());
                returnList.removeIf(s -> s != null && !s.startsWith(args[3]));
                return returnList;
            }
        }

        return Collections.emptyList();
    }
}

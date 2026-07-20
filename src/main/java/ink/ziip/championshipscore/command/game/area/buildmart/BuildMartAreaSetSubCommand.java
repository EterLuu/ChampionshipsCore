package ink.ziip.championshipscore.command.game.area.buildmart;

import ink.ziip.championshipscore.api.game.buildmart.BuildMartArea;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BuildMartAreaSetSubCommand extends BaseSubCommand {
    /** Simple single-location / single-region options (use the sender's current position). */
    private final String[] simpleArguments = {
            "spectator-spawn-point",
            "hub-spawn-point",
            "hub-pos1",
            "hub-pos2",
            "hub-return-pos1",
            "hub-return-pos2",
            "golden-display-point",
            "base",
    };

    /** Base template geometry keys: {@code /cc game area buildmart set <area> base <key>}. */
    private final String[] baseKeys = {
            "spawn",
            "portal-pos1",
            "portal-pos2",
            "normal-plot-1",
            "normal-plot-2",
            "normal-plot-3",
            "normal-ref-1",
            "normal-ref-2",
            "normal-ref-3",
            "golden-plot",
            "golden-ref",
    };

    public BuildMartAreaSetSubCommand() {
        super("set", "设置建材集市场地参数", "/cc game area buildmart set <场地> <参数> [基地键]");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 2 || !(sender instanceof Player player)) {
            sendUsage(sender);
            return true;
        }
        BuildMartArea buildMartArea = plugin.getGameManager().getBuildMartManager().getArea(args[0]);
        if (buildMartArea == null) {
            sender.sendMessage(MessageConfig.AREA_SETTING_OPTION_FAILED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]));
            return true;
        }
        BuildMartConfig config = buildMartArea.getGameConfig();

        if (args[1].equals("base")) {
            if (args.length != 3 || Arrays.stream(baseKeys).noneMatch(k -> k.equals(args[2]))) {
                sendUsage(sender);
                return true;
            }
            config.setBaseLocation(args[2], player.getLocation());
            sender.sendMessage(MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                    .replace("%area%", args[0])
                    .replace("%option%", "base." + args[2]));
            return true;
        }

        switch (args[1]) {
            case "spectator-spawn-point" -> config.setSpectatorSpawnPoint(player.getLocation());
            case "hub-spawn-point" -> config.setHubSpawnPoint(player.getLocation());
            case "hub-pos1" -> config.setHubPos1(player.getLocation().toVector());
            case "hub-pos2" -> config.setHubPos2(player.getLocation().toVector());
            case "hub-return-pos1" -> config.setHubReturnPos1(player.getLocation().toVector());
            case "hub-return-pos2" -> config.setHubReturnPos2(player.getLocation().toVector());
            case "golden-display-point" -> config.setGoldenDisplayPoint(player.getLocation());
            default -> {
                sender.sendMessage(MessageConfig.AREA_SETTING_OPTION_FAILED
                        .replace("%area%", args[0])
                        .replace("%option%", args[1]));
                return true;
            }
        }
        config.saveOptions();
        sender.sendMessage(MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                .replace("%area%", args[0])
                .replace("%option%", args[1]));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getGameManager().getBuildMartManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }
        if (args.length == 2) {
            List<String> returnList = new ArrayList<>(Arrays.asList(simpleArguments));
            returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
            return returnList;
        }
        if (args.length == 3 && args[1].equals("base")) {
            List<String> returnList = new ArrayList<>(Arrays.asList(baseKeys));
            returnList.removeIf(s -> s != null && !s.startsWith(args[2]));
            return returnList;
        }
        return Collections.emptyList();
    }
}

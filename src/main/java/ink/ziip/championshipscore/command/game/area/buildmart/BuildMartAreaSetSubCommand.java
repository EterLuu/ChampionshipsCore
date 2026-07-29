package ink.ziip.championshipscore.command.game.area.buildmart;

import ink.ziip.championshipscore.api.game.buildmart.BuildMartArea;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartConfig;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.Location;
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
            "normal-submit-1",
            "normal-submit-2",
            "normal-submit-3",
            "golden-plot",
            "golden-ref",
            "golden-submit",
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
            // Submit-button keys capture the admin's WorldEdit single-block selection (the button itself);
            // other base keys capture the admin's position (the plot/reference origin they're standing at).
            Location loc;
            if (isSubmitKey(args[2])) {
                loc = selectedSingleBlockLocation(player, sender);
                if (loc == null) return true; // error already sent
            } else {
                loc = player.getLocation();
            }
            config.setBaseLocation(args[2], loc);
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

    /** Whether {@code key} is a submit-button base key (captured by WorldEdit single-block selection). */
    private static boolean isSubmitKey(String key) {
        return key.equals("normal-submit-1") || key.equals("normal-submit-2")
                || key.equals("normal-submit-3") || key.equals("golden-submit");
    }

    /**
     * The admin's current WorldEdit selection as a single block, or {@code null} (with an error sent to
     * {@code sender}) when the selection is missing or spans more than one block. Used for submit-button
     * placement so the exact button block is captured without aiming ambiguity.
     */
    @Nullable
    private Location selectedSingleBlockLocation(Player player, CommandSender sender) {
        Vector[] selection;
        try {
            selection = plugin.getWorldEditManager().getPlayerSelection(player, true);
        } catch (Exception e) {
            Utils.sendAdminError(sender, "无法读取 WorldEdit 选区，请选中提交按钮方块。");
            return null;
        }
        Vector min = Vector.getMinimum(selection[0], selection[1]);
        Vector max = Vector.getMaximum(selection[0], selection[1]);
        int sx = max.getBlockX() - min.getBlockX() + 1;
        int sy = max.getBlockY() - min.getBlockY() + 1;
        int sz = max.getBlockZ() - min.getBlockZ() + 1;
        if (sx != 1 || sy != 1 || sz != 1) {
            Utils.sendAdminError(sender, "提交按钮选区必须为单一方块 #696969• #ededed当前 #fff566"
                    + sx + "×" + sy + "×" + sz);
            return null;
        }
        return new Location(player.getWorld(), min.getBlockX(), min.getBlockY(), min.getBlockZ());
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

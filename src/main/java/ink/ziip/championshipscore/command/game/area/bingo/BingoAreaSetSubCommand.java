package ink.ziip.championshipscore.command.game.area.bingo;

import ink.ziip.championshipscore.api.game.bingo.BingoArea;
import ink.ziip.championshipscore.api.game.bingo.BingoConfig;
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

public class BingoAreaSetSubCommand extends BaseSubCommand {
    // Bingo spans the whole world: no area-pos box, and players are scattered (no fixed spawn points).
    private final String[] arguments = {
            "spectator-spawn-point",
    };

    public BingoAreaSetSubCommand() {
        super("set", "设置宾果场地参数", "/cc game area bingo set <场地> <参数>");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }
        Player player = (Player) sender;
        BingoArea bingoArea = plugin.getGameManager().getBingoManager().getArea(args[0]);
        if (bingoArea == null) {
            sender.sendMessage(MessageConfig.AREA_SETTING_OPTION_FAILED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]));
            return true;
        }
        BingoConfig bingoConfig = bingoArea.getGameConfig();
        if (args.length == 2) {
            if (args[1].equals("spectator-spawn-point")) {
                bingoConfig.setSpectatorSpawnPoint(player.getLocation());
            }
            bingoConfig.saveOptions();
            sender.sendMessage(MessageConfig.AREA_SETTING_OPTION_SUCCEEDED
                    .replace("%area%", args[0])
                    .replace("%option%", args[1]));
            return true;
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = plugin.getGameManager().getBingoManager().getAreaNameList();
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }

        if (args.length == 2) {
            List<String> returnList = new ArrayList<>(Arrays.asList(arguments));
            returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
            return returnList;
        }

        return Collections.emptyList();
    }
}

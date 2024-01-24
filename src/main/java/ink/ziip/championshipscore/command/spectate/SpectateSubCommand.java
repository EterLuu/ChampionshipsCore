package ink.ziip.championshipscore.command.spectate;

import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
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

public class SpectateSubCommand extends BaseSubCommand {
    private final String[] games = {
            "leave",
            "battlebox",
            "parkourtag",
            "skywars",
    };

    public SpectateSubCommand() {
        super("spectate");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer((Player) sender);
        if (championshipTeam != null) {
            sender.sendMessage(MessageConfig.SPECTATOR_IS_PLAYER);
            return true;
        }

        if (args.length == 1) {
            if (args[0].equals("leave")) {
                if (plugin.getGameManager().leaveSpectating((Player) sender)) {
                    sender.sendMessage(MessageConfig.SPECTATOR_LEAVING_AREA);
                } else {
                    sender.sendMessage(MessageConfig.SPECTATOR_CANT_LEAVING_AREA);
                }
            }
        }
        if (args.length == 2) {
            GameTypeEnum gameTypeEnum = null;
            BaseArea baseArea = null;
            if (args[0].equals("battlebox")) {
                gameTypeEnum = GameTypeEnum.BattleBox;
                BaseArea battleBoxArea = plugin.getGameManager().getBattleBoxManager().getArea(args[1]);
                if (battleBoxArea != null) {
                    baseArea = battleBoxArea;
                }
            }
            if (args[0].equals("parkourtag")) {
                gameTypeEnum = GameTypeEnum.ParkourTag;
                BaseArea parkourTagArea = plugin.getGameManager().getParkourTagManager().getArea(args[1]);
                if (parkourTagArea != null) {
                    baseArea = parkourTagArea;
                }
            }
            if (args[0].equals("skywars")) {
                gameTypeEnum = GameTypeEnum.SkyWars;
                BaseArea skyWarsArea = plugin.getGameManager().getSkyWarsManager().getArea(args[1]);
                if (skyWarsArea != null) {
                    baseArea = skyWarsArea;
                }
            }
            if (gameTypeEnum != null && baseArea != null) {
                if (plugin.getGameManager().spectateArea((Player) sender, GameTypeEnum.BattleBox, args[1])) {
                    String message = MessageConfig.SPECTATOR_JOIN_AREA
                            .replace("%game%", gameTypeEnum.toString())
                            .replace("%area%", baseArea.getGameConfig().getAreaName());
                    sender.sendMessage(message);
                } else {
                    sender.sendMessage(MessageConfig.SPECTATOR_CANT_JOIN_AREA);
                }
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> returnList = new ArrayList<>(Arrays.asList(games));
            returnList.removeIf(s -> s != null && !s.startsWith(args[0]));
            return returnList;
        }
        if (args.length == 2) {
            if (args[0].equals("battlebox")) {
                List<String> returnList = plugin.getGameManager().getBattleBoxManager().getAreaNameList();
                returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
                return returnList;
            }
            if (args[0].equals("parkourtag")) {
                List<String> returnList = plugin.getGameManager().getParkourTagManager().getAreaNameList();
                returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
                return returnList;
            }
            if (args[0].equals("skywars")) {
                List<String> returnList = plugin.getGameManager().getSkyWarsManager().getAreaNameList();
                returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
                return returnList;
            }
        }
        return Collections.emptyList();
    }
}

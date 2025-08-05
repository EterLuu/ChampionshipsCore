package ink.ziip.championshipscore.command.spectate;

import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.CCConfig;
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
            "tgttos",
            "tntrun",
            "snowball",
            "dragoneggcarnival",
            "acc",
            "parkourwarrior",
            "hotycodydusky",
            "bingo"
    };

    public SpectateSubCommand() {
        super("spectate");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (CCConfig.STRICT_SPECTATOR_RULE) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer((Player) sender);
            if (plugin.getRankManager().getRound() != 7) {
                if (championshipTeam != null && !sender.hasPermission("cc.refuge")) {
                    sender.sendMessage(MessageConfig.SPECTATOR_IS_PLAYER);
                    return true;
                }
            }
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
            if (args[0].equals("tgttos")) {
                gameTypeEnum = GameTypeEnum.TGTTOS;
                BaseArea tgttosArea = plugin.getGameManager().getTgttosManager().getArea(args[1]);
                if (tgttosArea != null) {
                    baseArea = tgttosArea;
                }
            }
            if (args[0].equals("tntrun")) {
                gameTypeEnum = GameTypeEnum.TNTRun;
                BaseArea tntRunArea = plugin.getGameManager().getTntRunManager().getArea(args[1]);
                if (tntRunArea != null) {
                    baseArea = tntRunArea;
                }
            }
            if (args[0].equals("snowball")) {
                gameTypeEnum = GameTypeEnum.SnowballShowdown;
                BaseArea snowballArea = plugin.getGameManager().getSnowballShowdownManager().getArea(args[1]);
                if (snowballArea != null) {
                    baseArea = snowballArea;
                }
            }
            if (args[0].equals("dragoneggcarnival")) {
                gameTypeEnum = GameTypeEnum.DragonEggCarnival;
                BaseArea dragonEggCarnival = plugin.getGameManager().getDragonEggCarnivalManager().getArea(args[1]);
                if (dragonEggCarnival != null) {
                    baseArea = dragonEggCarnival;
                }
            }
            if (args[0].equals("acc")) {
                gameTypeEnum = GameTypeEnum.AdvancementCC;
                BaseArea advancementCCArea = plugin.getGameManager().getAdvancementCCManager().getArea(args[1]);
                if (advancementCCArea != null) {
                    baseArea = advancementCCArea;
                }
            }
            if (args[0].equals("parkourwarrior")) {
                gameTypeEnum = GameTypeEnum.ParkourWarrior;
                BaseArea parkourWarriorArea = plugin.getGameManager().getParkourWarriorManager().getArea(args[1]);
                if (parkourWarriorArea != null) {
                    baseArea = parkourWarriorArea;
                }
            }
            if (args[0].equals("hotycodydusky")) {
                gameTypeEnum = GameTypeEnum.HotyCodyDusky;
                BaseArea hotyCodyDuskyTeamArea = plugin.getGameManager().getHotyCodyDuskyManager().getArea(args[1]);
                if (hotyCodyDuskyTeamArea != null) {
                    baseArea = hotyCodyDuskyTeamArea;
                }
            }
            if (args[0].equals("bingo")) {
                gameTypeEnum = GameTypeEnum.Bingo;
                BaseArea bingoTeamArea = plugin.getGameManager().getBingoSpectatorManager().getArea(args[1]);
                if (bingoTeamArea != null) {
                    baseArea = bingoTeamArea;
                }
            }
            if (gameTypeEnum != null && baseArea != null) {
                if (plugin.getGameManager().spectateArea((Player) sender, baseArea)) {
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
            if (args[0].equals("tntrun")) {
                List<String> returnList = plugin.getGameManager().getTntRunManager().getAreaNameList();
                returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
                return returnList;
            }
            if (args[0].equals("tgttos")) {
                List<String> returnList = plugin.getGameManager().getTgttosManager().getAreaNameList();
                returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
                return returnList;
            }
            if (args[0].equals("snowball")) {
                List<String> returnList = plugin.getGameManager().getSnowballShowdownManager().getAreaNameList();
                returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
                return returnList;
            }
            if (args[0].equals("dragoneggcarnival")) {
                List<String> returnList = plugin.getGameManager().getDragonEggCarnivalManager().getAreaNameList();
                returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
                return returnList;
            }
            if (args[0].equals("acc")) {
                List<String> returnList = plugin.getGameManager().getAdvancementCCManager().getAreaNameList();
                returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
                return returnList;
            }
            if (args[0].equals("parkourwarrior")) {
                List<String> returnList = plugin.getGameManager().getParkourWarriorManager().getAreaNameList();
                returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
                return returnList;
            }
            if (args[0].equals("hotycodydusky")) {
                List<String> returnList = plugin.getGameManager().getHotyCodyDuskyManager().getAreaNameList();
                returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
                return returnList;
            }
            if (args[0].equals("bingo")) {
                List<String> returnList = plugin.getGameManager().getBingoSpectatorManager().getAreaNameList();
                returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
                return returnList;
            }
        }
        return Collections.emptyList();
    }
}

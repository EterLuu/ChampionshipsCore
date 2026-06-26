package ink.ziip.championshipscore.command.spectate;

import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.game.manager.BaseAreaManager;
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
import java.util.Map;

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

    /**
     * Maps the spectate keyword to its game type. Only games with a spectatable area
     * manager appear here; keywords such as {@code leave}, {@code acc} and {@code bingo}
     * are intentionally absent and fall through silently.
     */
    private static final Map<String, GameTypeEnum> SPECTATABLE_GAMES = Map.of(
            "battlebox", GameTypeEnum.BattleBox,
            "parkourtag", GameTypeEnum.ParkourTag,
            "skywars", GameTypeEnum.SkyWars,
            "tgttos", GameTypeEnum.TGTTOS,
            "tntrun", GameTypeEnum.TNTRun,
            "snowball", GameTypeEnum.SnowballShowdown,
            "dragoneggcarnival", GameTypeEnum.DragonEggCarnival,
            "parkourwarrior", GameTypeEnum.ParkourWarrior,
            "hotycodydusky", GameTypeEnum.HotyCodyDusky
    );

    public SpectateSubCommand() {
        super("spectate", "观战游戏（leave 退出观战）", "/cc spectate <游戏|leave> [场地]");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }
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
            GameTypeEnum gameTypeEnum = SPECTATABLE_GAMES.get(args[0]);
            if (gameTypeEnum == null) {
                return true;
            }
            BaseAreaManager<? extends BaseArea> manager = plugin.getGameManager().getAreaManager(gameTypeEnum);
            if (manager == null) {
                return true;
            }
            BaseArea baseArea = manager.getArea(args[1]);
            if (baseArea == null) {
                return true;
            }
            if (plugin.getGameManager().spectateArea((Player) sender, baseArea)) {
                String message = MessageConfig.SPECTATOR_JOIN_AREA
                        .replace("%game%", gameTypeEnum.toString())
                        .replace("%area%", baseArea.getGameConfig().getAreaName());
                sender.sendMessage(message);
            } else {
                sender.sendMessage(MessageConfig.SPECTATOR_CANT_JOIN_AREA);
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
            GameTypeEnum gameTypeEnum = SPECTATABLE_GAMES.get(args[0]);
            if (gameTypeEnum != null) {
                BaseAreaManager<? extends BaseArea> manager = plugin.getGameManager().getAreaManager(gameTypeEnum);
                if (manager != null) {
                    List<String> returnList = manager.getAreaNameList();
                    returnList.removeIf(s -> s != null && !s.startsWith(args[1]));
                    return returnList;
                }
            }
        }
        return Collections.emptyList();
    }
}

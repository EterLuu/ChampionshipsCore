package ink.ziip.championshipscore.command.spectate;

import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.BaseGameInstanceManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.command.BaseSubCommand;
import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.util.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class SpectateSubCommand extends BaseSubCommand {
    /**
     * Maps the spectate keyword to its game type. Only games with a spectatable area
     * manager appear here; the {@code leave} keyword is intentionally absent.
     */
    private static final Map<String, GameTypeEnum> SPECTATABLE_GAMES = Map.ofEntries(
            Map.entry("bingo", GameTypeEnum.Bingo),
            Map.entry("battlebox", GameTypeEnum.BattleBox),
            Map.entry("parkourtag", GameTypeEnum.ParkourTag),
            Map.entry("skywars", GameTypeEnum.SkyWars),
            Map.entry("tgttos", GameTypeEnum.TGTTOS),
            Map.entry("tntrun", GameTypeEnum.TNTRun),
            Map.entry("snowball", GameTypeEnum.SnowballShowdown),
            Map.entry("dragoneggcarnival", GameTypeEnum.DragonEggCarnival),
            Map.entry("parkourwarrior", GameTypeEnum.ParkourWarrior),
            Map.entry("hotycodydusky", GameTypeEnum.HotyCodyDusky),
            Map.entry("buildmart", GameTypeEnum.BuildMart),
            Map.entry("dodgebolt", GameTypeEnum.Dodgebolt),
            Map.entry("acerace", GameTypeEnum.AceRace)
    );

    public SpectateSubCommand() {
        super("spectate", "打开观战菜单或直接选择场地", "/cc spectate [leave | <游戏> <场地>]", PLAYER_PERMISSION);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Utils.sendAdminError(sender, "该命令只能由玩家执行");
            return true;
        }
        if (args.length > 2) {
            sendUsage(sender);
            return true;
        }
        if (args.length == 0) {
            if (plugin.getGameManager().canManuallySpectate(player)) {
                plugin.getGameManager().openSpectateMenu(player);
            }
            return true;
        }
        if (args.length == 1) {
            if (!args[0].equalsIgnoreCase("leave")) {
                sendUsage(sender);
                return true;
            }
            if (plugin.getGameManager().leaveSpectating(player)) {
                sender.sendMessage(MessageConfig.SPECTATOR_LEAVING_AREA);
            } else {
                sender.sendMessage(MessageConfig.SPECTATOR_CANT_LEAVING_AREA);
            }
            return true;
        }

        if (!plugin.getGameManager().canManuallySpectate(player)) return true;

        if (args.length == 2) {
            GameTypeEnum gameTypeEnum = SPECTATABLE_GAMES.get(args[0].toLowerCase(Locale.ROOT));
            if (gameTypeEnum == null) {
                sendUsage(sender);
                return true;
            }
            if (!plugin.getGameManager().isGameEnabled(gameTypeEnum)) {
                Utils.sendAdminError(sender, "该游戏当前未启用");
                return true;
            }
            BaseGameInstanceManager<? extends BaseGameInstance> manager = plugin.getGameManager().getAreaManager(gameTypeEnum);
            if (manager == null) {
                return true;
            }
            BaseGameInstance baseArea = manager.getArea(args[1]);
            if (baseArea == null) {
                Utils.sendAdminError(sender, "找不到场地 &#fff566" + args[1]);
                return true;
            }
            if (plugin.getGameManager().spectateArea(player, baseArea)) {
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
            List<String> returnList = new ArrayList<>();
            returnList.add("leave");
            for (Map.Entry<String, GameTypeEnum> entry : SPECTATABLE_GAMES.entrySet()) {
                if (plugin.getGameManager().isGameEnabled(entry.getValue())) {
                    returnList.add(entry.getKey());
                }
            }
            return filterStartsWith(returnList, args[0]);
        }
        if (args.length == 2) {
            GameTypeEnum gameTypeEnum = SPECTATABLE_GAMES.get(args[0].toLowerCase(Locale.ROOT));
            if (gameTypeEnum != null) {
                BaseGameInstanceManager<? extends BaseGameInstance> manager = plugin.getGameManager().getAreaManager(gameTypeEnum);
                if (manager != null) {
                    List<String> returnList = manager.getAreaNameList();
                    return filterStartsWith(returnList, args[1]);
                }
            }
        }
        return Collections.emptyList();
    }
}

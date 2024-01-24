package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxManager;
import ink.ziip.championshipscore.api.game.bingo.BingoTeamArea;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagArea;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager extends BaseManager {
    private final Map<UUID, Boolean> playerVisibleOption = new ConcurrentHashMap<>();
    private final Map<UUID, BaseArea> playerSpectatorStatus = new ConcurrentHashMap<>();
    private final Map<ChampionshipTeam, BaseArea> teamStatus = new ConcurrentHashMap<>();
    private final Map<UUID, BaseArea> playerStatus = new ConcurrentHashMap<>();
    private final GameManagerHandler gameManagerHandler;
    @Getter
    private final BattleBoxManager battleBoxManager;
    @Getter
    private final ParkourTagManager parkourTagManager;

    public GameManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        gameManagerHandler = new GameManagerHandler(championshipsCore);
        battleBoxManager = new BattleBoxManager(plugin);
        parkourTagManager = new ParkourTagManager(plugin);
    }

    @Override
    public void load() {
        battleBoxManager.load();
        parkourTagManager.load();

        gameManagerHandler.register();
    }

    @Override
    public void unload() {
        battleBoxManager.unload();
        parkourTagManager.unload();
        gameManagerHandler.unRegister();
    }

    @Nullable
    public BaseArea getBaseTeamArea(ChampionshipTeam championshipTeam) {
        return teamStatus.get(championshipTeam);
    }

    @Nullable
    public BaseArea getPlayerSpectatorStatus(UUID uuid) {
        return playerSpectatorStatus.get(uuid);
    }

    public boolean joinTeamArea(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area, @NotNull ChampionshipTeam rightChampionshipTeam, @NotNull ChampionshipTeam leftChampionshipTeam) {
        for (UUID uuid : rightChampionshipTeam.getMembers()) {
            if (playerStatus.containsKey(uuid))
                return false;
            if (playerSpectatorStatus.containsKey(uuid))
                return false;
        }
        for (UUID uuid : leftChampionshipTeam.getMembers()) {
            if (playerStatus.containsKey(uuid))
                return false;
            if (playerSpectatorStatus.containsKey(uuid))
                return false;
        }
        if (teamStatus.containsKey(rightChampionshipTeam))
            return false;
        if (teamStatus.containsKey(leftChampionshipTeam))
            return false;

        if (gameTypeEnum == GameTypeEnum.BattleBox) {
            BattleBoxArea battleBoxArea = getBattleBoxManager().getArea(area);
            if (battleBoxArea == null)
                return false;
            if (battleBoxArea.tryStartGame(rightChampionshipTeam, leftChampionshipTeam)) {
                teamStatus.put(rightChampionshipTeam, battleBoxArea);
                teamStatus.put(leftChampionshipTeam, battleBoxArea);
                return true;
            }
            return false;
        }

        if (gameTypeEnum == GameTypeEnum.ParkourTag) {
            ParkourTagArea parkourTagArea = getParkourTagManager().getArea(area);
            if (parkourTagArea == null)
                return false;
            if (parkourTagArea.tryStartGame(rightChampionshipTeam, leftChampionshipTeam)) {
                teamStatus.put(rightChampionshipTeam, parkourTagArea);
                teamStatus.put(leftChampionshipTeam, parkourTagArea);
                return true;
            }
            return false;
        }

        return true;
    }

    public boolean joinSingleTeamAreaForAllTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area) {
        for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
            if (teamStatus.containsKey(championshipTeam))
                return false;
        }

        if (gameTypeEnum == GameTypeEnum.Bingo) {
            if (plugin.getBingoManager().isStarted())
                return false;

            plugin.getBingoManager().startGame();
            for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                BingoTeamArea bingoArea = new BingoTeamArea(plugin);
                teamStatus.put(championshipTeam, bingoArea);
            }
            return true;
        }

    public void teamGameEndHandler(TeamGameEndEvent event) {
        teamStatus.remove(event.getLeftChampionshipTeam());
        teamStatus.remove(event.getRightChampionshipTeam());
    }

    public void singleTeamGameEndHandler(SingleGameEndEvent event) {
        for (ChampionshipTeam championshipTeam : event.getChampionshipTeams())
            teamStatus.remove(championshipTeam);
    }

    public boolean spectateArea(@NotNull Player player, @NotNull GameTypeEnum gameTypeEnum, @NotNull String area) {
        UUID uuid = player.getUniqueId();
        if (gameTypeEnum == GameTypeEnum.BattleBox) {
            BattleBoxArea battleBoxArea = getBattleBoxManager().getArea(area);
            if (battleBoxArea != null) {
                if (playerSpectatorStatus.containsKey(uuid)) {
                    return false;
                }
                playerSpectatorStatus.put(uuid, battleBoxArea);
                battleBoxArea.addSpectator(player);
            }
        }
        if (gameTypeEnum == GameTypeEnum.ParkourTag) {
            ParkourTagArea parkourTagArea = getParkourTagManager().getArea(area);
            if (parkourTagArea != null) {
                if (playerSpectatorStatus.containsKey(uuid)) {
                    return false;
                }
                playerSpectatorStatus.put(uuid, parkourTagArea);
                parkourTagArea.addSpectator(player);
            }
        }

        return false;
    }

    public boolean leaveSpectating(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (playerSpectatorStatus.containsKey(uuid)) {
            BaseArea baseArea = playerSpectatorStatus.get(uuid);
            baseArea.removeSpectator(player);
            playerSpectatorStatus.remove(uuid);
            return true;
        }

        return false;
    }

    public boolean removeSpectatingPlayerFromList(@NotNull UUID uuid) {
        if (playerSpectatorStatus.containsKey(uuid)) {
            BaseArea baseArea = playerSpectatorStatus.get(uuid);
            baseArea.onlyRemoveSpectatorFromList(uuid);
            playerSpectatorStatus.remove(uuid);
            return true;
        }

        return false;
    }

    public void updatePlayer(@NotNull Player player) {
        setPlayerVisible(player, getPlayerVisible(player));
    }

    public boolean getPlayerVisible(@NotNull Player player) {
        return playerVisibleOption.getOrDefault(player.getUniqueId(), true);
    }

    public void setPlayerVisible(@NotNull Player player, boolean visible) {
        UUID uuid = player.getUniqueId();
        playerVisibleOption.put(uuid, visible);
        if (visible) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.showPlayer(plugin, player);
            }
        } else {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.hidePlayer(plugin, player);
            }
            for (Map.Entry<UUID, Boolean> playerVisibleOptionEntry : playerVisibleOption.entrySet()) {
                Player playerEntry = Bukkit.getPlayer(playerVisibleOptionEntry.getKey());
                if (playerEntry != null) {
                    if (!playerVisibleOptionEntry.getValue()) {
                        playerEntry.showPlayer(plugin, player);
                    }
                }
            }
        }
    }
}

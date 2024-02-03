package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxManager;
import ink.ziip.championshipscore.api.game.bingo.BingoTeamArea;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalArea;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalManager;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagArea;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagManager;
import ink.ziip.championshipscore.api.game.skywars.SkyWarsTeamArea;
import ink.ziip.championshipscore.api.game.skywars.SkyWarsManager;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownManager;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownTeamArea;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSManager;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSTeamArea;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunManager;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunTeamArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager extends BaseManager {
    private final Map<UUID, BaseArea> playerSpectatorStatus = new ConcurrentHashMap<>();
    private final Map<ChampionshipTeam, BaseArea> teamStatus = new ConcurrentHashMap<>();
    private final Map<UUID, BaseArea> playerStatus = new ConcurrentHashMap<>();
    private final GameManagerHandler gameManagerHandler;
    @Getter
    private final BattleBoxManager battleBoxManager;
    @Getter
    private final ParkourTagManager parkourTagManager;
    @Getter
    private final SkyWarsManager skyWarsManager;
    @Getter
    private final TGTTOSManager tgttosManager;
    @Getter
    private final TNTRunManager tntRunManager;
    @Getter
    private final DragonEggCarnivalManager dragonEggCarnivalManager;
    @Getter
    private final SnowballShowdownManager snowballShowdownManager;

    public GameManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        gameManagerHandler = new GameManagerHandler(championshipsCore);
        battleBoxManager = new BattleBoxManager(plugin);
        parkourTagManager = new ParkourTagManager(plugin);
        skyWarsManager = new SkyWarsManager(plugin);
        tgttosManager = new TGTTOSManager(plugin);
        tntRunManager = new TNTRunManager(plugin);
        dragonEggCarnivalManager = new DragonEggCarnivalManager(plugin);
        snowballShowdownManager = new SnowballShowdownManager(plugin);
    }

    @Override
    public void load() {
        battleBoxManager.load();
        parkourTagManager.load();
        skyWarsManager.load();
        tgttosManager.load();
        tntRunManager.load();
        dragonEggCarnivalManager.load();
        snowballShowdownManager.load();

        gameManagerHandler.register();
    }

    @Override
    public void unload() {
        battleBoxManager.unload();
        parkourTagManager.unload();
        skyWarsManager.unload();
        tgttosManager.unload();
        tntRunManager.unload();
        dragonEggCarnivalManager.unload();
        snowballShowdownManager.unload();

        gameManagerHandler.unRegister();
    }

    public boolean joinTeamArea(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area, @NotNull ChampionshipTeam rightChampionshipTeam, @NotNull ChampionshipTeam leftChampionshipTeam) {
        for (UUID uuid : rightChampionshipTeam.getMembers()) {
            if (playerStatus.containsKey(uuid))
                return false;
            if (playerSpectatorStatus.containsKey(uuid))
                removeSpectator(uuid);
        }
        for (UUID uuid : leftChampionshipTeam.getMembers()) {
            if (playerStatus.containsKey(uuid))
                return false;
            if (playerSpectatorStatus.containsKey(uuid))
                removeSpectator(uuid);
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
                addPlayerStatusByTeam(rightChampionshipTeam, battleBoxArea);
                addPlayerStatusByTeam(leftChampionshipTeam, battleBoxArea);
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
                addPlayerStatusByTeam(rightChampionshipTeam, parkourTagArea);
                addPlayerStatusByTeam(leftChampionshipTeam, parkourTagArea);
                return true;
            }
            return false;
        }

        if (gameTypeEnum == GameTypeEnum.DragonEggCarnival) {
            DragonEggCarnivalArea dragonEggCarnivalArea = getDragonEggCarnivalManager().getArea(area);
            if (dragonEggCarnivalArea == null)
                return false;
            if (dragonEggCarnivalArea.tryStartGame(rightChampionshipTeam, leftChampionshipTeam)) {
                teamStatus.put(rightChampionshipTeam, dragonEggCarnivalArea);
                teamStatus.put(leftChampionshipTeam, dragonEggCarnivalArea);
                addPlayerStatusByTeam(rightChampionshipTeam, dragonEggCarnivalArea);
                addPlayerStatusByTeam(leftChampionshipTeam, dragonEggCarnivalArea);
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

        for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
            for (UUID uuid : championshipTeam.getMembers()) {
                removeSpectator(uuid);
            }
        }

        if (gameTypeEnum == GameTypeEnum.Bingo) {
            if (plugin.getBingoManager().isStarted())
                return false;

            plugin.getBingoManager().startGame();
            BingoTeamArea bingoArea = new BingoTeamArea(plugin, null, null);
            for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                teamStatus.put(championshipTeam, bingoArea);
                addPlayerStatusByTeam(championshipTeam, bingoArea);
            }
            return true;
        }

        if (gameTypeEnum == GameTypeEnum.SkyWars) {
            SkyWarsTeamArea skyWarsArea = skyWarsManager.getArea(area);
            if (skyWarsArea == null)
                return false;

            if (skyWarsArea.tryStartGame(plugin.getTeamManager().getTeamList())) {
                for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                    teamStatus.put(championshipTeam, skyWarsArea);
                    addPlayerStatusByTeam(championshipTeam, skyWarsArea);
                }
                return true;
            }
            return false;
        }

        if (gameTypeEnum == GameTypeEnum.TGTTOS) {
            TGTTOSTeamArea tgttosTeamArea = tgttosManager.getArea(area);
            if (tgttosTeamArea == null)
                return false;

            if (tgttosTeamArea.tryStartGame(plugin.getTeamManager().getTeamList())) {
                for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                    teamStatus.put(championshipTeam, tgttosTeamArea);
                    addPlayerStatusByTeam(championshipTeam, tgttosTeamArea);
                }
                return true;
            }

            return false;
        }

        if (gameTypeEnum == GameTypeEnum.TNTRun) {
            TNTRunTeamArea tntRunArea = tntRunManager.getArea(area);
            if (tntRunArea == null)
                return false;

            if (tntRunArea.tryStartGame(plugin.getTeamManager().getTeamList())) {
                for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                    teamStatus.put(championshipTeam, tntRunArea);
                    addPlayerStatusByTeam(championshipTeam, tntRunArea);
                }
                return true;
            }
            return false;
        }

        if (gameTypeEnum == GameTypeEnum.SnowballShowdown) {
            SnowballShowdownTeamArea snowballShowdownTeamArea = snowballShowdownManager.getArea(area);
            if (snowballShowdownTeamArea == null)
                return false;

            if (snowballShowdownTeamArea.tryStartGame(plugin.getTeamManager().getTeamList())) {
                for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                    teamStatus.put(championshipTeam, snowballShowdownTeamArea);
                    addPlayerStatusByTeam(championshipTeam, snowballShowdownTeamArea);
                }
                return true;
            }
            return false;
        }

        return false;
    }

    public String getPlayerCurrentAreaName(UUID uuid) {
        BaseArea baseArea = playerStatus.get(uuid);

        if (baseArea instanceof BingoTeamArea)
            return "";

        if (baseArea != null)
            return baseArea.getGameConfig().getConfigName();

        baseArea = playerSpectatorStatus.get(uuid);

        if (baseArea != null)
            return baseArea.getGameConfig().getConfigName();

        return "";
    }

    public BaseArea getTeamCurrenArea(ChampionshipTeam championshipTeam) {
        return teamStatus.get(championshipTeam);
    }

    private void addPlayerStatusByTeam(ChampionshipTeam championshipTeam, BaseArea baseArea) {
        for (UUID uuid : championshipTeam.getMembers()) {
            playerStatus.put(uuid, baseArea);
        }
    }

    public void removePlayerStatusByTeam(ChampionshipTeam championshipTeam) {
        for (UUID uuid : championshipTeam.getMembers()) {
            playerStatus.remove(uuid);
        }
    }

    public void teamGameEndHandler(TeamGameEndEvent event) {
        teamStatus.remove(event.getLeftChampionshipTeam());
        teamStatus.remove(event.getRightChampionshipTeam());
        removePlayerStatusByTeam(event.getLeftChampionshipTeam());
        removePlayerStatusByTeam(event.getRightChampionshipTeam());
    }

    public void singleTeamGameEndHandler(SingleGameEndEvent event) {
        for (ChampionshipTeam championshipTeam : event.getChampionshipTeams()) {
            teamStatus.remove(championshipTeam);
            removePlayerStatusByTeam(championshipTeam);
        }
    }

    @Nullable
    public BaseArea getBaseTeamArea(ChampionshipTeam championshipTeam) {
        return teamStatus.get(championshipTeam);
    }

    @Nullable
    public BaseArea getPlayerSpectatorStatus(UUID uuid) {
        return playerSpectatorStatus.get(uuid);
    }

    public synchronized boolean spectateArea(@NotNull Player player, @NotNull BaseArea baseArea) {
        UUID uuid = player.getUniqueId();
        if (playerSpectatorStatus.containsKey(uuid)) {
            return false;
        }
        if (playerStatus.containsKey(uuid)) {
            return false;
        }

        playerSpectatorStatus.put(uuid, baseArea);
        baseArea.addSpectator(player);
        return true;
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

    public void removeSpectator(@NotNull UUID uuid) {
        if (playerSpectatorStatus.containsKey(uuid)) {
            BaseArea baseArea = playerSpectatorStatus.get(uuid);
            baseArea.removeSpectator(uuid);
            playerSpectatorStatus.remove(uuid);
        }
    }

    public void removeSpectatingPlayerFromList(@NotNull UUID uuid) {
        if (playerSpectatorStatus.containsKey(uuid)) {
            BaseArea baseArea = playerSpectatorStatus.get(uuid);
            baseArea.onlyRemoveSpectatorFromList(uuid);
            playerSpectatorStatus.remove(uuid);
        }
    }
}

package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.api.game.area.team.BaseTeamArea;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxManager;
import ink.ziip.championshipscore.api.game.bingo.BingoManager;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalManager;
import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyManager;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagManager;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorManager;
import ink.ziip.championshipscore.api.game.skywars.SkyWarsManager;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownManager;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSManager;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
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
    @Getter
    private final ParkourWarriorManager parkourWarriorManager;
    @Getter
    private final HotyCodyDuskyManager hotyCodyDuskyManager;
    @Getter
    private final BingoManager bingoManager;
    /**
     * Registry mapping each game type to its area manager. Drives the generic
     * {@code join*} dispatch so adding a game only requires registering it here.
     */
    private final Map<GameTypeEnum, BaseAreaManager<? extends BaseArea>> areaManagers = new EnumMap<>(GameTypeEnum.class);

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
        parkourWarriorManager = new ParkourWarriorManager(plugin);
        hotyCodyDuskyManager = new HotyCodyDuskyManager(plugin);
        bingoManager = new BingoManager(plugin);

        areaManagers.put(GameTypeEnum.Bingo, bingoManager);
        areaManagers.put(GameTypeEnum.BattleBox, battleBoxManager);
        areaManagers.put(GameTypeEnum.ParkourTag, parkourTagManager);
        areaManagers.put(GameTypeEnum.SkyWars, skyWarsManager);
        areaManagers.put(GameTypeEnum.TGTTOS, tgttosManager);
        areaManagers.put(GameTypeEnum.TNTRun, tntRunManager);
        areaManagers.put(GameTypeEnum.DragonEggCarnival, dragonEggCarnivalManager);
        areaManagers.put(GameTypeEnum.SnowballShowdown, snowballShowdownManager);
        areaManagers.put(GameTypeEnum.ParkourWarrior, parkourWarriorManager);
        areaManagers.put(GameTypeEnum.HotyCodyDusky, hotyCodyDuskyManager);
    }

    /**
     * @return the area manager registered for {@code gameTypeEnum}, or {@code null} if none.
     */
    @Nullable
    public BaseAreaManager<? extends BaseArea> getAreaManager(GameTypeEnum gameTypeEnum) {
        return areaManagers.get(gameTypeEnum);
    }

    @Override
    public void load() {
        for (BaseAreaManager<? extends BaseArea> manager : areaManagers.values()) {
            manager.load();
        }

        gameManagerHandler.register();
    }

    @Override
    public void unload() {
        for (BaseAreaManager<? extends BaseArea> manager : areaManagers.values()) {
            manager.unload();
        }

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

        BaseAreaManager<? extends BaseArea> manager = areaManagers.get(gameTypeEnum);
        if (manager == null)
            return false;
        if (!(manager.getArea(area) instanceof BaseTeamArea teamArea))
            return false;

        if (teamArea.tryStartGame(rightChampionshipTeam, leftChampionshipTeam)) {
            teamStatus.put(rightChampionshipTeam, teamArea);
            teamStatus.put(leftChampionshipTeam, teamArea);
            addPlayerStatusByTeam(rightChampionshipTeam, teamArea);
            addPlayerStatusByTeam(leftChampionshipTeam, teamArea);
            return true;
        }
        return false;
    }

    public synchronized boolean joinSingleTeamAreaForTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area, @NotNull ChampionshipTeam... championshipTeams) {
        for (ChampionshipTeam championshipTeam : championshipTeams) {
            if (teamStatus.containsKey(championshipTeam))
                return false;
        }

        for (ChampionshipTeam championshipTeam : championshipTeams) {
            for (UUID uuid : championshipTeam.getMembers()) {
                removeSpectator(uuid);
            }
        }

        BaseAreaManager<? extends BaseArea> manager = areaManagers.get(gameTypeEnum);
        if (manager == null)
            return false;
        if (!(manager.getArea(area) instanceof BaseSingleTeamArea singleTeamArea))
            return false;

        if (singleTeamArea.tryStartGame(List.of(championshipTeams))) {
            for (ChampionshipTeam championshipTeam : championshipTeams) {
                teamStatus.put(championshipTeam, singleTeamArea);
                addPlayerStatusByTeam(championshipTeam, singleTeamArea);
            }
            return true;
        }

        return false;
    }

    public synchronized boolean joinSingleTeamAreaForPlayers(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area, List<UUID> players) {
        for (UUID playerUUID : players) {
            if (playerStatus.containsKey(playerUUID))
                return false;
        }

        Set<ChampionshipTeam> championshipTeams = new HashSet<>();
        for (UUID playerUUID : players) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(playerUUID);
            if (championshipTeam == null)
                return false;

            championshipTeams.add(championshipTeam);
        }

        BaseAreaManager<? extends BaseArea> manager = areaManagers.get(gameTypeEnum);
        if (manager == null)
            return false;
        if (!(manager.getArea(area) instanceof BaseSingleTeamArea singleTeamArea))
            return false;

        for (UUID playerUUID : players) {
            removeSpectator(playerUUID);
        }

        if (singleTeamArea.tryStartGame(championshipTeams.stream().toList(), players)) {
            for (UUID playerUUID : players) {
                playerStatus.put(playerUUID, singleTeamArea);
            }
            return true;
        }

        return false;
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

        BaseAreaManager<? extends BaseArea> manager = areaManagers.get(gameTypeEnum);
        if (manager == null)
            return false;
        if (!(manager.getArea(area) instanceof BaseSingleTeamArea singleTeamArea))
            return false;

        if (singleTeamArea.tryStartGame(plugin.getTeamManager().getTeamList())) {
            for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                teamStatus.put(championshipTeam, singleTeamArea);
                addPlayerStatusByTeam(championshipTeam, singleTeamArea);
            }
            return true;
        }

        return false;
    }

    public String getPlayerCurrentAreaName(UUID uuid) {
        BaseArea baseArea = playerStatus.get(uuid);

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
    public BaseArea getBasePlayerArea(UUID uuid) {
        return playerStatus.get(uuid);
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

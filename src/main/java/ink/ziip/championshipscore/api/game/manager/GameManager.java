package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.area.BaseArea;
import ink.ziip.championshipscore.api.game.area.single.BaseSingleTeamArea;
import ink.ziip.championshipscore.api.game.area.team.BaseTeamArea;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxManager;
import ink.ziip.championshipscore.api.game.bingo.BingoManager;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartManager;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalManager;
import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyManager;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagArea;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagManager;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorManager;
import ink.ziip.championshipscore.api.game.skywars.SkyWarsManager;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownManager;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSManager;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunManager;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.schedule.TwoVTwoVector;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

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
    @Getter
    private final BuildMartManager buildMartManager;
    /**
     * Registry mapping each game type to its area manager. Drives the generic
     * {@code join*} dispatch so adding a game only requires registering it here.
     */
    private final Map<GameTypeEnum, BaseAreaManager<? extends BaseArea>> areaManagers = new EnumMap<>(GameTypeEnum.class);
    /** Lazily parsed from {@link CCConfig#ENABLED_GAMES}; see {@link #getEnabledGames()}. */
    private Set<GameTypeEnum> enabledGames;

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
        buildMartManager = new BuildMartManager(plugin);

        areaManagers.put(GameTypeEnum.Bingo, bingoManager);
        areaManagers.put(GameTypeEnum.BuildMart, buildMartManager);
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

    /**
     * @return true if {@code gameTypeEnum} is listed in the {@code enabled-games} config option.
     * Only enabled games load their area worlds and can be started or operated.
     */
    public boolean isGameEnabled(@NotNull GameTypeEnum gameTypeEnum) {
        return getEnabledGames().contains(gameTypeEnum);
    }

    /**
     * @return the games enabled via the {@code enabled-games} config option. Parsed lazily on first
     * use (the configuration file is loaded after this manager is constructed), case-insensitively;
     * unknown names are logged once and ignored. An empty list means no game is enabled.
     */
    public Set<GameTypeEnum> getEnabledGames() {
        if (enabledGames == null) {
            Set<GameTypeEnum> parsed = EnumSet.noneOf(GameTypeEnum.class);
            List<String> configured = CCConfig.ENABLED_GAMES;
            if (configured != null) {
                for (String name : configured) {
                    if (name == null)
                        continue;
                    String trimmed = name.trim();
                    boolean matched = false;
                    for (GameTypeEnum type : GameTypeEnum.values()) {
                        if (type.name().equalsIgnoreCase(trimmed)) {
                            parsed.add(type);
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        plugin.getLogger().log(Level.WARNING, Utils.formatModuleLog("GameManager", "配置",
                                "enabled-games 包含未知游戏=" + trimmed + "，已忽略"));
                    }
                }
            }
            enabledGames = parsed;
            plugin.getLogger().log(Level.INFO, Utils.formatModuleLog("GameManager", "加载",
                    "已启用游戏=" + (parsed.isEmpty() ? "无" : parsed)));
        }
        return enabledGames;
    }

    @Override
    public void load() {
        for (Map.Entry<GameTypeEnum, BaseAreaManager<? extends BaseArea>> entry : areaManagers.entrySet()) {
            if (isGameEnabled(entry.getKey())) {
                entry.getValue().load();
            } else {
                plugin.getLogger().log(Level.INFO, Utils.formatGameLog(entry.getKey(), "-", "加载", "跳过",
                        "游戏未启用，不加载场地与世界"));
            }
        }

        gameManagerHandler.register();
    }

    @Override
    public void unload() {
        for (Map.Entry<GameTypeEnum, BaseAreaManager<? extends BaseArea>> entry : areaManagers.entrySet()) {
            if (isGameEnabled(entry.getKey()))
                entry.getValue().unload();
        }

        gameManagerHandler.unRegister();
    }

    /**
     * Force-ends every currently-running area of the given game (any area not in WAITING). Used by the
     * schedule "delete current game" flow to scrap a broken/in-progress game before clearing its records.
     * Calls {@link BaseArea#endGameFinally()}, which kicks players/spectators out and resets the area.
     */
    public void forceEndAreas(@NotNull GameTypeEnum gameTypeEnum) {
        BaseAreaManager<?> manager = areaManagers.get(gameTypeEnum);
        if (manager == null) return;
        for (String name : manager.getAreaNameList()) {
            BaseArea area = manager.getArea(name);
            if (area != null && area.getGameStageEnum() != GameStageEnum.WAITING) {
                area.endGameFinally();
            }
        }
    }

    public boolean joinTeamArea(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area, @NotNull ChampionshipTeam rightChampionshipTeam, @NotNull ChampionshipTeam leftChampionshipTeam) {
        return joinTeamArea(gameTypeEnum, area, rightChampionshipTeam, leftChampionshipTeam, true);
    }

    public boolean joinTeamArea(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                @NotNull ChampionshipTeam rightChampionshipTeam,
                                @NotNull ChampionshipTeam leftChampionshipTeam, boolean showIntroduction) {
        if (!isGameEnabled(gameTypeEnum))
            return false;
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

        teamArea.setIntroductionEnabledForNextStart(showIntroduction);
        if (teamArea.tryStartGame(rightChampionshipTeam, leftChampionshipTeam)) {
            teamStatus.put(rightChampionshipTeam, teamArea);
            teamStatus.put(leftChampionshipTeam, teamArea);
            addPlayerStatusByTeam(rightChampionshipTeam, teamArea);
            addPlayerStatusByTeam(leftChampionshipTeam, teamArea);
            return true;
        }
        teamArea.setIntroductionEnabledForNextStart(true);
        return false;
    }

    public synchronized boolean joinSingleTeamAreaForTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area, @NotNull ChampionshipTeam... championshipTeams) {
        return joinSingleTeamAreaForTeams(gameTypeEnum, area, true, championshipTeams);
    }

    public synchronized boolean joinSingleTeamAreaForTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                            boolean showIntroduction,
                                                            @NotNull ChampionshipTeam... championshipTeams) {
        if (!isGameEnabled(gameTypeEnum))
            return false;
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

        singleTeamArea.setIntroductionEnabledForNextStart(showIntroduction);
        if (singleTeamArea.tryStartGame(List.of(championshipTeams))) {
            for (ChampionshipTeam championshipTeam : championshipTeams) {
                teamStatus.put(championshipTeam, singleTeamArea);
                addPlayerStatusByTeam(championshipTeam, singleTeamArea);
            }
            return true;
        }

        singleTeamArea.setIntroductionEnabledForNextStart(true);
        return false;
    }

    public synchronized boolean joinSingleTeamAreaForPlayers(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area, List<UUID> players) {
        return joinSingleTeamAreaForPlayers(gameTypeEnum, area, players, true);
    }

    public synchronized boolean joinSingleTeamAreaForPlayers(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                              List<UUID> players, boolean showIntroduction) {
        if (!isGameEnabled(gameTypeEnum))
            return false;
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

        singleTeamArea.setIntroductionEnabledForNextStart(showIntroduction);
        if (singleTeamArea.tryStartGame(championshipTeams.stream().toList(), players)) {
            for (UUID playerUUID : players) {
                playerStatus.put(playerUUID, singleTeamArea);
            }
            return true;
        }

        singleTeamArea.setIntroductionEnabledForNextStart(true);
        return false;
    }

    public boolean joinSingleTeamAreaForAllTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area) {
        return joinSingleTeamAreaForAllTeams(gameTypeEnum, area, true);
    }

    public boolean joinSingleTeamAreaForAllTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                  boolean showIntroduction) {
        if (!isGameEnabled(gameTypeEnum))
            return false;
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

        singleTeamArea.setIntroductionEnabledForNextStart(showIntroduction);
        if (singleTeamArea.tryStartGame(plugin.getTeamManager().getTeamList())) {
            for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                teamStatus.put(championshipTeam, singleTeamArea);
                addPlayerStatusByTeam(championshipTeam, singleTeamArea);
            }
            return true;
        }

        singleTeamArea.setIntroductionEnabledForNextStart(true);
        return false;
    }

    /**
     * Starts a round of parallel Battle Box matches: every {@link TwoVTwoVector} pairing runs in its own
     * stamped arena copy of the one Battle Box area. Mirrors the team-status bookkeeping of the other join
     * methods. Used by both the manual start command (one pair) and the Swiss schedule (a round's pairs).
     */
    public synchronized boolean joinBattleBoxArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs) {
        return joinBattleBoxArea(area, pairs, true);
    }

    public synchronized boolean joinBattleBoxArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs,
                                                   boolean showIntroduction) {
        if (!isGameEnabled(GameTypeEnum.BattleBox))
            return false;
        Set<ChampionshipTeam> teams = new LinkedHashSet<>();
        for (TwoVTwoVector pair : pairs) {
            teams.add(pair.getTeamOne());
            teams.add(pair.getTeamTwo());
        }
        for (ChampionshipTeam team : teams) {
            if (teamStatus.containsKey(team))
                return false;
            for (UUID uuid : team.getMembers()) {
                if (playerStatus.containsKey(uuid))
                    return false;
            }
        }
        if (!(battleBoxManager.getArea(area) instanceof BattleBoxArea battleBoxArea))
            return false;

        for (ChampionshipTeam team : teams) {
            for (UUID uuid : team.getMembers()) {
                removeSpectator(uuid);
            }
        }

        battleBoxArea.setIntroductionEnabledForNextStart(showIntroduction);
        if (battleBoxArea.tryStartMatches(pairs)) {
            for (ChampionshipTeam team : teams) {
                teamStatus.put(team, battleBoxArea);
                addPlayerStatusByTeam(team, battleBoxArea);
            }
            return true;
        }
        battleBoxArea.setIntroductionEnabledForNextStart(true);
        return false;
    }

    /** Battle-Box-style parallel start for Parkour Tag: each pairing runs in its own stamped arena copy. */
    public synchronized boolean joinParkourTagArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs) {
        return joinParkourTagArea(area, pairs, true);
    }

    public synchronized boolean joinParkourTagArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs,
                                                    boolean showIntroduction) {
        if (!isGameEnabled(GameTypeEnum.ParkourTag))
            return false;
        Set<ChampionshipTeam> teams = new LinkedHashSet<>();
        for (TwoVTwoVector pair : pairs) {
            teams.add(pair.getTeamOne());
            teams.add(pair.getTeamTwo());
        }
        for (ChampionshipTeam team : teams) {
            if (teamStatus.containsKey(team))
                return false;
            for (UUID uuid : team.getMembers()) {
                if (playerStatus.containsKey(uuid))
                    return false;
            }
        }
        if (!(parkourTagManager.getArea(area) instanceof ParkourTagArea parkourTagArea))
            return false;

        for (ChampionshipTeam team : teams) {
            for (UUID uuid : team.getMembers()) {
                removeSpectator(uuid);
            }
        }

        parkourTagArea.setIntroductionEnabledForNextStart(showIntroduction);
        if (parkourTagArea.tryStartMatches(pairs)) {
            for (ChampionshipTeam team : teams) {
                teamStatus.put(team, parkourTagArea);
                addPlayerStatusByTeam(team, parkourTagArea);
            }
            return true;
        }
        parkourTagArea.setIntroductionEnabledForNextStart(true);
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

package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.game.spectate.SpectateMenu;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxManager;
import ink.ziip.championshipscore.api.game.bingo.BingoManager;
import ink.ziip.championshipscore.api.game.bingo.execution.BingoExecutionRouter;
import ink.ziip.championshipscore.api.game.bingo.execution.BingoStartRequest;
import ink.ziip.championshipscore.api.game.bingo.execution.LocalBingoExecutionGateway;
import ink.ziip.championshipscore.api.game.bingo.execution.RemoteBingoInstance;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartManager;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalManager;
import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltManager;
import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltArea;
import ink.ziip.championshipscore.api.game.acerace.AceRaceManager;
import ink.ziip.championshipscore.api.game.hotycodydusky.HotyCodyDuskyManager;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagArea;
import ink.ziip.championshipscore.api.game.parkourtag.ParkourTagManager;
import ink.ziip.championshipscore.api.game.parkourwarrior.ParkourWarriorManager;
import ink.ziip.championshipscore.api.game.skywars.SkyWarsManager;
import ink.ziip.championshipscore.api.game.snowball.SnowballShowdownManager;
import ink.ziip.championshipscore.api.game.tgttos.TGTTOSManager;
import ink.ziip.championshipscore.api.game.tntrun.TNTRunManager;
import ink.ziip.championshipscore.api.object.game.GameRunMode;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.object.schedule.TwoVTwoVector;
import ink.ziip.championshipscore.api.object.stage.GameStageEnum;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.configuration.config.CCConfig;
import ink.ziip.championshipscore.util.Utils;
import lombok.Getter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class GameManager extends BaseManager {
    private final Map<UUID, BaseGameInstance> playerSpectatorStatus = new ConcurrentHashMap<>();
    private final Map<ChampionshipTeam, BaseGameInstance> teamStatus = new ConcurrentHashMap<>();
    private final Map<UUID, BaseGameInstance> playerStatus = new ConcurrentHashMap<>();
    private final Map<UUID, RoundTransitionHold> roundTransitionHolds = new ConcurrentHashMap<>();
    private final Map<UUID, SpectatorTransitionHold> spectatorTransitionHolds = new ConcurrentHashMap<>();
    private final Map<UUID, RemoteBingoInstance> remoteBingoInstances = new ConcurrentHashMap<>();
    private final GameManagerHandler gameManagerHandler;
    private final SpectateMenu spectateMenu;
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
    private final BingoExecutionRouter bingoExecutionRouter;
    @Getter
    private final BuildMartManager buildMartManager;
    @Getter
    private final DodgeboltManager dodgeboltManager;
    @Getter
    private final AceRaceManager aceRaceManager;
    /**
     * Registry mapping each game type to its area manager. Drives the generic
     * {@code join*} dispatch so adding a game only requires registering it here.
     */
    private final Map<GameTypeEnum, BaseGameInstanceManager<? extends BaseGameInstance>> areaManagers = new EnumMap<>(GameTypeEnum.class);
    /** Lazily parsed from {@link CCConfig#ENABLED_GAMES}; see {@link #getEnabledGames()}. */
    private Set<GameTypeEnum> enabledGames;
    /** Representative instance used for automatic audience routing, including gaps between formal rounds. */
    private volatile BaseGameInstance spectatorFocus;
    /** Managers that have actually been loaded, including disabled games opened through map editing. */
    private final Set<GameTypeEnum> loadedGameManagers = EnumSet.noneOf(GameTypeEnum.class);

    public GameManager(ChampionshipsCore championshipsCore) {
        super(championshipsCore);
        gameManagerHandler = new GameManagerHandler(championshipsCore);
        spectateMenu = new SpectateMenu(championshipsCore, this);
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
        dodgeboltManager = new DodgeboltManager(plugin);
        aceRaceManager = new AceRaceManager(plugin);

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
        areaManagers.put(GameTypeEnum.Dodgebolt, dodgeboltManager);
        areaManagers.put(GameTypeEnum.AceRace, aceRaceManager);

        bingoExecutionRouter = new BingoExecutionRouter(new LocalBingoExecutionGateway(request ->
                request.teams().isEmpty()
                        ? joinSingleTeamAreaForAllTeamsLocal(GameTypeEnum.Bingo, request.area(),
                                request.showIntroduction(), request.runMode())
                        : joinSingleTeamAreaForTeams(GameTypeEnum.Bingo, request.area(),
                                request.showIntroduction(), request.runMode(),
                                request.teams().toArray(ChampionshipTeam[]::new)),
                ignored -> forceEndLocalAreas(GameTypeEnum.Bingo)));
    }

    /**
     * @return the area manager registered for {@code gameTypeEnum}, or {@code null} if none.
     */
    @Nullable
    public BaseGameInstanceManager<? extends BaseGameInstance> getAreaManager(GameTypeEnum gameTypeEnum) {
        return areaManagers.get(gameTypeEnum);
    }

    public boolean isGameManagerLoaded(@NotNull GameTypeEnum gameTypeEnum) {
        return loadedGameManagers.contains(gameTypeEnum);
    }

    /** Returns a bound map's admin teleport anchor, or {@code null} when the world is unbound. */
    @Nullable
    public Location getMapTeleportLocation(@NotNull String worldName) {
        for (GameTypeEnum gameType : GameTypeEnum.values()) {
            BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameType);
            if (manager == null) continue;
            Location target = manager.getWorldTeleportLocation(worldName);
            if (target != null) return target;
        }
        return null;
    }

    /**
     * Loads an otherwise disabled game's manager for map preparation. The operation is idempotent so
     * reopening its map UI cannot register worlds, listeners, or runtime instances twice.
     *
     * @return true when a load was started; false when the manager was already available or absent.
     */
    public boolean loadGameForEditing(@NotNull GameTypeEnum gameTypeEnum) {
        return loadGameManager(gameTypeEnum);
    }

    private boolean loadGameManager(@NotNull GameTypeEnum gameTypeEnum) {
        BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameTypeEnum);
        if (manager == null || !loadedGameManagers.add(gameTypeEnum)) return false;
        manager.load();
        plugin.getLogger().log(Level.INFO, Utils.formatGameLog(gameTypeEnum, "-", "加载", "完成",
                "地图管理器已加载"));
        return true;
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
        for (GameTypeEnum gameType : areaManagers.keySet()) {
            if (isGameEnabled(gameType)) {
                loadGameManager(gameType);
            } else {
                plugin.getLogger().log(Level.INFO, Utils.formatGameLog(gameType, "-", "加载", "跳过",
                        "游戏未启用，不加载场地与世界"));
            }
        }

        gameManagerHandler.register();
        spectateMenu.start();
    }

    @Override
    public void unload() {
        spectateMenu.stop();
        for (GameTypeEnum gameType : EnumSet.copyOf(loadedGameManagers)) {
            BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameType);
            if (manager != null) manager.unload();
        }
        loadedGameManagers.clear();
        enabledGames = null;
        spectatorFocus = null;
        roundTransitionHolds.clear();
        spectatorTransitionHolds.clear();
        remoteBingoInstances.values().forEach(RemoteBingoInstance::dispose);
        remoteBingoInstances.clear();

        gameManagerHandler.unRegister();
    }

    /**
     * Force-ends every currently-running area of the given game (any area not in WAITING). Used by the
     * schedule "delete current game" flow to scrap a broken/in-progress game before clearing its records.
     * Calls {@link BaseGameInstance#endGameFinally()}, which removes players and resets the instance.
     */
    public void forceEndAreas(@NotNull GameTypeEnum gameTypeEnum) {
        if (gameTypeEnum == GameTypeEnum.Bingo) {
            bingoExecutionRouter.forceEnd("formal-event-force-end");
            return;
        }
        forceEndLocalAreas(gameTypeEnum);
    }

    private void forceEndLocalAreas(@NotNull GameTypeEnum gameTypeEnum) {
        BaseGameInstanceManager<?> manager = areaManagers.get(gameTypeEnum);
        if (manager == null) return;
        for (BaseGameInstance instance : manager.getRuntimeInstances()) {
            if (instance.getGameStageEnum() != GameStageEnum.WAITING) {
                instance.endGameFinally();
            }
        }
    }

    public boolean joinTeamArea(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area, @NotNull ChampionshipTeam rightChampionshipTeam, @NotNull ChampionshipTeam leftChampionshipTeam) {
        return joinTeamArea(gameTypeEnum, area, rightChampionshipTeam, leftChampionshipTeam, false);
    }

    /** Starts the non-scoring final and records which finalist owns both opening arrows. */
    public boolean joinDodgeboltArea(@NotNull String area, @NotNull ChampionshipTeam rightTeam,
                                     @NotNull ChampionshipTeam leftTeam,
                                     @NotNull ChampionshipTeam higherSeed, boolean showIntroduction) {
        return joinDodgeboltArea(area, rightTeam, leftTeam, higherSeed, showIntroduction, false);
    }

    /** Forced starts admit each team's currently-online subset while normal finals still require full rosters. */
    public boolean joinDodgeboltArea(@NotNull String area, @NotNull ChampionshipTeam rightTeam,
                                     @NotNull ChampionshipTeam leftTeam,
                                     @NotNull ChampionshipTeam higherSeed, boolean showIntroduction,
                                     boolean forcePartialRoster) {
        return joinDodgeboltArea(area, rightTeam, leftTeam, higherSeed, showIntroduction,
                forcePartialRoster, GameRunMode.GAME);
    }

    public boolean joinDodgeboltArea(@NotNull String area, @NotNull ChampionshipTeam rightTeam,
                                     @NotNull ChampionshipTeam leftTeam,
                                     @NotNull ChampionshipTeam higherSeed, boolean showIntroduction,
                                     boolean forcePartialRoster, @NotNull GameRunMode runMode) {
        DodgeboltArea instance = dodgeboltManager.getArea(area);
        if (instance == null || (!higherSeed.equals(rightTeam) && !higherSeed.equals(leftTeam))) return false;
        instance.setFirstRoundArrowTeam(higherSeed);
        boolean started = joinTeamArea(GameTypeEnum.Dodgebolt, area, rightTeam, leftTeam,
                showIntroduction, forcePartialRoster, runMode);
        if (!started) instance.setFirstRoundArrowTeam(null);
        return started;
    }

    /** Moves every online non-finalist into the final's spectator set without strict-spectator checks. */
    public void spectateDodgeboltFinal(@NotNull DodgeboltArea area,
                                       @NotNull ChampionshipTeam rightTeam,
                                       @NotNull ChampionshipTeam leftTeam) {
        if (!area.isEventRun()) return;
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (rightTeam.isTeamMember(player) || leftTeam.isTeamMember(player)) continue;
            moveSpectatorTo(player, area);
        }
        spectatorFocus = area;
    }

    public boolean joinTeamArea(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                @NotNull ChampionshipTeam rightChampionshipTeam,
                                @NotNull ChampionshipTeam leftChampionshipTeam, boolean showIntroduction) {
        return joinTeamArea(gameTypeEnum, area, rightChampionshipTeam, leftChampionshipTeam,
                showIntroduction, false, GameRunMode.GAME);
    }

    public boolean joinTeamArea(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                @NotNull ChampionshipTeam rightChampionshipTeam,
                                @NotNull ChampionshipTeam leftChampionshipTeam, boolean showIntroduction,
                                @NotNull GameRunMode runMode) {
        return joinTeamArea(gameTypeEnum, area, rightChampionshipTeam, leftChampionshipTeam,
                showIntroduction, false, runMode);
    }

    private boolean joinTeamArea(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                 @NotNull ChampionshipTeam rightChampionshipTeam,
                                 @NotNull ChampionshipTeam leftChampionshipTeam, boolean showIntroduction,
                                 boolean forcePartialDodgeboltRoster, @NotNull GameRunMode runMode) {
        if (!isGameEnabled(gameTypeEnum))
            return false;
        if (!plugin.getPrepareSessionManager().canStart(gameTypeEnum, area))
            return false;
        Collection<UUID> rightParticipants = forcePartialDodgeboltRoster
                ? rightChampionshipTeam.getOnlinePlayers().stream().map(Player::getUniqueId).toList()
                : rightChampionshipTeam.getMembers();
        Collection<UUID> leftParticipants = forcePartialDodgeboltRoster
                ? leftChampionshipTeam.getOnlinePlayers().stream().map(Player::getUniqueId).toList()
                : leftChampionshipTeam.getMembers();
        if (rightParticipants.isEmpty() || leftParticipants.isEmpty())
            return false;
        for (UUID uuid : rightParticipants) {
            if (isPlayerUnavailableForStart(uuid, gameTypeEnum, showIntroduction, runMode))
                return false;
            if (playerSpectatorStatus.containsKey(uuid))
                removeSpectator(uuid);
        }
        for (UUID uuid : leftParticipants) {
            if (isPlayerUnavailableForStart(uuid, gameTypeEnum, showIntroduction, runMode))
                return false;
            if (playerSpectatorStatus.containsKey(uuid))
                removeSpectator(uuid);
        }
        if (teamStatus.containsKey(rightChampionshipTeam))
            return false;
        if (teamStatus.containsKey(leftChampionshipTeam))
            return false;

        BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameTypeEnum);
        if (manager == null)
            return false;
        if (!(manager.getArea(area) instanceof BasePairedGameInstance teamArea))
            return false;

        teamArea.prepareRunMode(runMode);
        teamArea.setIntroductionEnabledForNextStart(showIntroduction);
        boolean started = teamArea instanceof DodgeboltArea dodgeboltArea
                ? dodgeboltArea.tryStartGame(rightChampionshipTeam, leftChampionshipTeam,
                        forcePartialDodgeboltRoster)
                : teamArea.tryStartGame(rightChampionshipTeam, leftChampionshipTeam);
        if (started) {
            teamStatus.put(rightChampionshipTeam, teamArea);
            teamStatus.put(leftChampionshipTeam, teamArea);
            if (forcePartialDodgeboltRoster) {
                for (UUID uuid : teamArea.getParticipantUniqueIds()) {
                    playerStatus.put(uuid, teamArea);
                    roundTransitionHolds.remove(uuid);
                }
            } else {
                addPlayerStatusByTeam(rightChampionshipTeam, teamArea);
                addPlayerStatusByTeam(leftChampionshipTeam, teamArea);
            }
            focusSpectatorsOn(teamArea);
            return true;
        }
        teamArea.prepareRunMode(GameRunMode.GAME);
        teamArea.setIntroductionEnabledForNextStart(false);
        return false;
    }

    public synchronized boolean joinSingleTeamAreaForTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area, @NotNull ChampionshipTeam... championshipTeams) {
        return joinSingleTeamAreaForTeams(gameTypeEnum, area, false, championshipTeams);
    }

    public synchronized boolean joinSingleTeamAreaForTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                            boolean showIntroduction,
                                                            @NotNull ChampionshipTeam... championshipTeams) {
        return joinSingleTeamAreaForTeams(gameTypeEnum, area, showIntroduction,
                GameRunMode.GAME, championshipTeams);
    }

    public synchronized boolean joinSingleTeamAreaForTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                            boolean showIntroduction, @NotNull GameRunMode runMode,
                                                            @NotNull ChampionshipTeam... championshipTeams) {
        if (!isGameEnabled(gameTypeEnum))
            return false;
        if (!plugin.getPrepareSessionManager().canStart(gameTypeEnum, area))
            return false;
        for (ChampionshipTeam championshipTeam : championshipTeams) {
            if (teamStatus.containsKey(championshipTeam))
                return false;
            for (UUID uuid : championshipTeam.getMembers()) {
                if (isPlayerUnavailableForStart(uuid, gameTypeEnum, showIntroduction, runMode))
                    return false;
            }
        }

        for (ChampionshipTeam championshipTeam : championshipTeams) {
            for (UUID uuid : championshipTeam.getMembers()) {
                removeSpectator(uuid);
            }
        }

        BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameTypeEnum);
        if (manager == null)
            return false;
        if (!(manager.getArea(area) instanceof BaseMultiTeamGameInstance singleTeamArea))
            return false;

        return joinMultiTeamInstanceForTeams(gameTypeEnum, singleTeamArea, showIntroduction,
                runMode, List.of(championshipTeams));
    }

    /** Starts an explicitly selected runtime slot, used by same-map DAILY replicas such as Ace Race. */
    public synchronized boolean joinMultiTeamInstanceForTeams(
            @NotNull GameTypeEnum gameTypeEnum, @NotNull BaseMultiTeamGameInstance singleTeamArea,
            boolean showIntroduction, @NotNull GameRunMode runMode,
            @NotNull List<ChampionshipTeam> championshipTeams) {
        if (!isGameEnabled(gameTypeEnum) || singleTeamArea.getGameTypeEnum() != gameTypeEnum)
            return false;
        String mapName = singleTeamArea.getGameConfig().getConfigName();
        if (!plugin.getPrepareSessionManager().canStart(gameTypeEnum, mapName))
            return false;
        for (ChampionshipTeam championshipTeam : championshipTeams) {
            if (teamStatus.containsKey(championshipTeam)) return false;
            for (UUID uuid : championshipTeam.getMembers()) {
                if (isPlayerUnavailableForStart(uuid, gameTypeEnum, showIntroduction, runMode)) return false;
            }
        }
        for (ChampionshipTeam championshipTeam : championshipTeams)
            for (UUID uuid : championshipTeam.getMembers()) removeSpectator(uuid);

        singleTeamArea.prepareRunMode(runMode);
        singleTeamArea.setIntroductionEnabledForNextStart(showIntroduction);
        if (singleTeamArea.tryStartGame(championshipTeams)) {
            for (ChampionshipTeam championshipTeam : championshipTeams) {
                teamStatus.put(championshipTeam, singleTeamArea);
                addPlayerStatusByTeam(championshipTeam, singleTeamArea);
            }
            focusSpectatorsOn(singleTeamArea);
            return true;
        }

        singleTeamArea.prepareRunMode(GameRunMode.GAME);
        singleTeamArea.setIntroductionEnabledForNextStart(false);
        return false;
    }

    /** Public-play Bingo entry that carries an explicit transient roster through local or remote execution. */
    public boolean joinBingoForTeams(@NotNull String area, boolean showIntroduction,
                                     @NotNull GameRunMode runMode,
                                     @NotNull List<ChampionshipTeam> teams) {
        if (teams.isEmpty()) return false;
        return bingoExecutionRouter.start(new BingoStartRequest(area, showIntroduction, runMode, teams));
    }

    public synchronized boolean joinSingleTeamAreaForPlayers(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area, List<UUID> players) {
        return joinSingleTeamAreaForPlayers(gameTypeEnum, area, players, false);
    }

    public synchronized boolean joinSingleTeamAreaForPlayers(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                              List<UUID> players, boolean showIntroduction) {
        return joinSingleTeamAreaForPlayers(gameTypeEnum, area, players, showIntroduction, GameRunMode.GAME);
    }

    public synchronized boolean joinSingleTeamAreaForPlayers(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                              List<UUID> players, boolean showIntroduction,
                                                              @NotNull GameRunMode runMode) {
        if (!isGameEnabled(gameTypeEnum))
            return false;
        if (!plugin.getPrepareSessionManager().canStart(gameTypeEnum, area))
            return false;
        for (UUID playerUUID : players) {
            if (isPlayerUnavailableForStart(playerUUID, gameTypeEnum, showIntroduction, runMode))
                return false;
        }

        Set<ChampionshipTeam> championshipTeams = new HashSet<>();
        for (UUID playerUUID : players) {
            ChampionshipTeam championshipTeam = plugin.getTeamManager().getTeamByPlayer(playerUUID);
            if (championshipTeam == null)
                return false;

            championshipTeams.add(championshipTeam);
        }

        BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameTypeEnum);
        if (manager == null)
            return false;
        if (!(manager.getArea(area) instanceof BaseMultiTeamGameInstance singleTeamArea))
            return false;

        for (UUID playerUUID : players) {
            removeSpectator(playerUUID);
        }

        singleTeamArea.prepareRunMode(runMode);
        singleTeamArea.setIntroductionEnabledForNextStart(showIntroduction);
        if (singleTeamArea.tryStartGame(championshipTeams.stream().toList(), players)) {
            for (UUID playerUUID : players) {
                playerStatus.put(playerUUID, singleTeamArea);
                roundTransitionHolds.remove(playerUUID);
            }
            focusSpectatorsOn(singleTeamArea);
            return true;
        }

        singleTeamArea.prepareRunMode(GameRunMode.GAME);
        singleTeamArea.setIntroductionEnabledForNextStart(false);
        return false;
    }

    public boolean joinSingleTeamAreaForAllTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area) {
        return joinSingleTeamAreaForAllTeams(gameTypeEnum, area, false);
    }

    public boolean joinSingleTeamAreaForAllTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                  boolean showIntroduction) {
        return joinSingleTeamAreaForAllTeams(gameTypeEnum, area, showIntroduction, GameRunMode.GAME);
    }

    public boolean joinSingleTeamAreaForAllTeams(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                                  boolean showIntroduction, @NotNull GameRunMode runMode) {
        if (gameTypeEnum == GameTypeEnum.Bingo) {
            return bingoExecutionRouter.start(new BingoStartRequest(area, showIntroduction, runMode));
        }
        return joinSingleTeamAreaForAllTeamsLocal(gameTypeEnum, area, showIntroduction, runMode);
    }

    /** Non-mutating execution-plane readiness check used before committing a Bingo event round. */
    public boolean canStartBingo(@NotNull String area, boolean showIntroduction,
                                 @NotNull GameRunMode runMode) {
        return bingoExecutionRouter.canStart(new BingoStartRequest(area, showIntroduction, runMode));
    }

    private boolean joinSingleTeamAreaForAllTeamsLocal(
            @NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
            boolean showIntroduction, @NotNull GameRunMode runMode) {
        if (!isGameEnabled(gameTypeEnum))
            return false;
        if (!plugin.getPrepareSessionManager().canStart(gameTypeEnum, area))
            return false;
        for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
            if (teamStatus.containsKey(championshipTeam))
                return false;
            for (UUID uuid : championshipTeam.getMembers()) {
                if (isPlayerUnavailableForStart(uuid, gameTypeEnum, showIntroduction, runMode))
                    return false;
            }
        }

        for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
            for (UUID uuid : championshipTeam.getMembers()) {
                removeSpectator(uuid);
            }
        }

        BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameTypeEnum);
        if (manager == null)
            return false;
        if (!(manager.getArea(area) instanceof BaseMultiTeamGameInstance singleTeamArea))
            return false;

        singleTeamArea.prepareRunMode(runMode);
        singleTeamArea.setIntroductionEnabledForNextStart(showIntroduction);
        if (singleTeamArea.tryStartGame(plugin.getTeamManager().getTeamList())) {
            for (ChampionshipTeam championshipTeam : plugin.getTeamManager().getTeamList()) {
                teamStatus.put(championshipTeam, singleTeamArea);
                addPlayerStatusByTeam(championshipTeam, singleTeamArea);
            }
            focusSpectatorsOn(singleTeamArea);
            return true;
        }

        singleTeamArea.prepareRunMode(GameRunMode.GAME);
        singleTeamArea.setIntroductionEnabledForNextStart(false);
        return false;
    }

    /** Atomically reserves the normal team/player ownership maps for one remote Bingo execution. */
    public synchronized boolean reserveRemoteBingo(@NotNull RemoteBingoInstance instance,
                                                   @NotNull GameRunMode runMode,
                                                   boolean showIntroduction) {
        return reserveRemoteBingo(instance, runMode, showIntroduction, plugin.getTeamManager().getTeamList());
    }

    public synchronized boolean reserveRemoteBingo(@NotNull RemoteBingoInstance instance,
                                                   @NotNull GameRunMode runMode,
                                                   boolean showIntroduction,
                                                   @NotNull List<ChampionshipTeam> teams) {
        if (!canReserveRemoteBingo(runMode, showIntroduction, teams)) return false;
        if (!instance.reserve(teams, runMode)) return false;

        for (ChampionshipTeam team : teams) {
            teamStatus.put(team, instance);
            for (UUID playerId : team.getMembers()) {
                removeSpectator(playerId);
                playerStatus.put(playerId, instance);
                roundTransitionHolds.remove(playerId);
            }
        }
        remoteBingoInstances.put(instance.matchId(), instance);
        if (runMode == GameRunMode.EVENT) spectatorFocus = instance;
        return true;
    }

    /** Checks the same ownership constraints as {@link #reserveRemoteBingo} without changing them. */
    public synchronized boolean canReserveRemoteBingo(@NotNull GameRunMode runMode,
                                                       boolean showIntroduction) {
        return canReserveRemoteBingo(runMode, showIntroduction, plugin.getTeamManager().getTeamList());
    }

    public synchronized boolean canReserveRemoteBingo(@NotNull GameRunMode runMode,
                                                       boolean showIntroduction,
                                                       @NotNull List<ChampionshipTeam> teams) {
        if (!isGameEnabled(GameTypeEnum.Bingo)) return false;
        if (teams.stream().flatMap(team -> team.getOnlinePlayers().stream()).findAny().isEmpty()) return false;
        for (ChampionshipTeam team : teams) {
            if (teamStatus.containsKey(team)) return false;
            for (UUID playerId : team.getMembers()) {
                if (isPlayerUnavailableForStart(playerId, GameTypeEnum.Bingo,
                        showIntroduction, runMode)) return false;
            }
        }
        return true;
    }

    /** Unteamed online viewers frozen into the remote manifest and owned until settlement. */
    public synchronized Set<UUID> reserveRemoteBingoSpectators(@NotNull RemoteBingoInstance instance) {
        if (!instance.isEventRun()) return Set.of();
        Set<UUID> result = new LinkedHashSet<>();
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (plugin.getTeamManager().getTeamByPlayer(player) != null) continue;
            UUID playerId = player.getUniqueId();
            BaseGameInstance previous = playerSpectatorStatus.get(playerId);
            if (previous != null && previous != instance) previous.onlyRemoveSpectatorFromList(playerId);
            playerSpectatorStatus.put(playerId, instance);
            instance.addSpectatorWithoutTeleport(playerId);
            result.add(playerId);
        }
        return Set.copyOf(result);
    }

    public synchronized void abortRemoteBingo(@NotNull RemoteBingoInstance instance) {
        boolean interruptedEvent = instance.isEventRun();
        releaseInstanceParticipants(instance);
        List<UUID> spectatorIds = playerSpectatorStatus.entrySet().stream()
                .filter(entry -> entry.getValue() == instance).map(Map.Entry::getKey).toList();
        for (UUID spectatorId : spectatorIds) {
            playerSpectatorStatus.remove(spectatorId, instance);
            instance.onlyRemoveSpectatorFromList(spectatorId);
        }
        remoteBingoInstances.remove(instance.matchId(), instance);
        instance.abortFromRemote();
        if (plugin.getDailyManager() != null) plugin.getDailyManager().abort(instance);
        instance.dispose();
        if (interruptedEvent) {
            plugin.getScheduleManager().abortFormalEvent(GameTypeEnum.Bingo,
                    "远端执行中止，已释放 Core 侧队伍与玩家占用");
        }
    }

    /** Starts one or more independent Battle Box instances from a shared map definition. */
    public synchronized boolean joinBattleBoxArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs) {
        return joinBattleBoxArea(area, pairs, false);
    }

    public synchronized boolean joinBattleBoxArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs,
                                                   boolean showIntroduction) {
        return joinBattleBoxInstances(area, pairs, showIntroduction) != null;
    }

    /** Returns the exact instances started for round-completion tracking, or {@code null} on failure. */
    public synchronized @Nullable List<BattleBoxArea> joinBattleBoxInstances(
            @NotNull String area, @NotNull List<TwoVTwoVector> pairs, boolean showIntroduction) {
        return joinBattleBoxInstances(area, pairs, showIntroduction, GameRunMode.GAME);
    }

    public synchronized @Nullable List<BattleBoxArea> joinBattleBoxInstances(
            @NotNull String area, @NotNull List<TwoVTwoVector> pairs, boolean showIntroduction,
            @NotNull GameRunMode runMode) {
        if (!isGameEnabled(GameTypeEnum.BattleBox))
            return null;
        if (!plugin.getPrepareSessionManager().canStart(GameTypeEnum.BattleBox, area))
            return null;
        if (pairs.isEmpty())
            return null;
        Set<ChampionshipTeam> teams = new LinkedHashSet<>();
        for (TwoVTwoVector pair : pairs) {
            teams.add(pair.getTeamOne());
            teams.add(pair.getTeamTwo());
        }
        if (teams.size() != pairs.size() * 2)
            return null;
        for (ChampionshipTeam team : teams) {
            if (teamStatus.containsKey(team))
                return null;
            for (UUID uuid : team.getMembers()) {
                if (isPlayerUnavailableForStart(uuid, GameTypeEnum.BattleBox, showIntroduction, runMode))
                    return null;
            }
        }
        List<BattleBoxArea> pool = battleBoxManager.getMapInstances(area);
        List<BattleBoxArea> selected = pool.stream()
                .filter(instance -> instance.getGameStageEnum() == GameStageEnum.WAITING)
                .limit(pairs.size())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (selected.size() < pairs.size())
            return null;

        java.util.concurrent.CompletableFuture<Void> startGate = new java.util.concurrent.CompletableFuture<>();
        selected.forEach(instance -> instance.coordinateStartWith(startGate));

        for (ChampionshipTeam team : teams) {
            for (UUID uuid : team.getMembers()) {
                removeSpectator(uuid);
            }
        }

        for (int i = 0; i < pairs.size(); i++) {
            BattleBoxArea instance = selected.get(i);
            TwoVTwoVector pair = pairs.get(i);
            instance.prepareRunMode(runMode);
            instance.setIntroductionEnabledForNextStart(showIntroduction);
            if (!instance.tryStartGame(pair.getTeamOne(), pair.getTeamTwo())) {
                startGate.complete(null);
                instance.setIntroductionEnabledForNextStart(false);
                return null;
            }
            teamStatus.put(pair.getTeamOne(), instance);
            teamStatus.put(pair.getTeamTwo(), instance);
            addPlayerStatusByTeam(pair.getTeamOne(), instance);
            addPlayerStatusByTeam(pair.getTeamTwo(), instance);
        }
        java.util.concurrent.CompletableFuture.allOf(selected.stream()
                .map(BattleBoxArea::getStartPreloadFuture)
                .toArray(java.util.concurrent.CompletableFuture[]::new))
                .whenComplete((unused, error) -> plugin.getServer().getScheduler()
                        .runTask(plugin, () -> startGate.complete(null)));
        focusSpectatorsOn(selected.getFirst());
        return List.copyOf(selected);
    }

    /** Battle-Box-style parallel start for Parkour Tag: each pairing runs in its own stamped arena copy. */
    public synchronized boolean joinParkourTagArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs) {
        return joinParkourTagArea(area, pairs, false);
    }

    public synchronized boolean joinParkourTagArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs,
                                                    boolean showIntroduction) {
        return joinParkourTagInstances(area, pairs, showIntroduction) != null;
    }

    /** Returns the exact instances started for round-completion tracking, or {@code null} on failure. */
    public synchronized @Nullable List<ParkourTagArea> joinParkourTagInstances(
            @NotNull String area, @NotNull List<TwoVTwoVector> pairs, boolean showIntroduction) {
        return joinParkourTagInstances(area, pairs, showIntroduction, GameRunMode.GAME);
    }

    public synchronized @Nullable List<ParkourTagArea> joinParkourTagInstances(
            @NotNull String area, @NotNull List<TwoVTwoVector> pairs, boolean showIntroduction,
            @NotNull GameRunMode runMode) {
        if (!isGameEnabled(GameTypeEnum.ParkourTag))
            return null;
        if (!plugin.getPrepareSessionManager().canStart(GameTypeEnum.ParkourTag, area))
            return null;
        if (pairs.isEmpty())
            return null;
        Set<ChampionshipTeam> teams = new LinkedHashSet<>();
        for (TwoVTwoVector pair : pairs) {
            teams.add(pair.getTeamOne());
            teams.add(pair.getTeamTwo());
        }
        if (teams.size() != pairs.size() * 2)
            return null;
        for (ChampionshipTeam team : teams) {
            if (teamStatus.containsKey(team))
                return null;
            for (UUID uuid : team.getMembers()) {
                if (isPlayerUnavailableForStart(uuid, GameTypeEnum.ParkourTag, showIntroduction, runMode))
                    return null;
            }
        }
        List<ParkourTagArea> pool = parkourTagManager.getMapInstances(area);
        List<ParkourTagArea> selected = pool.stream()
                .filter(instance -> instance.getGameStageEnum() == GameStageEnum.WAITING)
                .limit(pairs.size())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (selected.size() < pairs.size())
            return null;

        java.util.concurrent.CompletableFuture<Void> startGate = new java.util.concurrent.CompletableFuture<>();
        selected.forEach(instance -> instance.coordinateStartWith(startGate));

        for (ChampionshipTeam team : teams) {
            for (UUID uuid : team.getMembers()) {
                removeSpectator(uuid);
            }
        }

        for (int i = 0; i < pairs.size(); i++) {
            ParkourTagArea instance = selected.get(i);
            TwoVTwoVector pair = pairs.get(i);
            instance.prepareRunMode(runMode);
            instance.setIntroductionEnabledForNextStart(showIntroduction);
            if (!instance.tryStartGame(pair.getTeamOne(), pair.getTeamTwo())) {
                startGate.complete(null);
                instance.setIntroductionEnabledForNextStart(false);
                return null;
            }
            teamStatus.put(pair.getTeamOne(), instance);
            teamStatus.put(pair.getTeamTwo(), instance);
            addPlayerStatusByTeam(pair.getTeamOne(), instance);
            addPlayerStatusByTeam(pair.getTeamTwo(), instance);
        }
        java.util.concurrent.CompletableFuture.allOf(selected.stream()
                .map(ParkourTagArea::getStartPreloadFuture)
                .toArray(java.util.concurrent.CompletableFuture[]::new))
                .whenComplete((unused, error) -> plugin.getServer().getScheduler()
                        .runTask(plugin, () -> startGate.complete(null)));
        focusSpectatorsOn(selected.getFirst());
        return List.copyOf(selected);
    }

    public BaseGameInstance getTeamCurrenArea(ChampionshipTeam championshipTeam) {
        BaseGameInstance active = teamStatus.get(championshipTeam);
        if (active != null) return active;
        for (UUID uuid : championshipTeam.getMembers()) {
            RoundTransitionHold hold = roundTransitionHolds.get(uuid);
            if (hold != null) return hold.instance();
        }
        return null;
    }

    private void addPlayerStatusByTeam(ChampionshipTeam championshipTeam, BaseGameInstance baseArea) {
        for (UUID uuid : championshipTeam.getMembers()) {
            playerStatus.put(uuid, baseArea);
            roundTransitionHolds.remove(uuid);
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null && plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidate(player);
        }
    }

    private boolean isPlayerUnavailableForStart(UUID uuid, GameTypeEnum gameType, boolean showIntroduction,
                                                GameRunMode requestedMode) {
        if (playerStatus.containsKey(uuid)) return true;
        RoundTransitionHold hold = roundTransitionHolds.get(uuid);
        if (hold == null) return false;
        return requestedMode != GameRunMode.EVENT || showIntroduction
                || hold.mode() != GameRunMode.EVENT
               || hold.instance().getGameTypeEnum() != gameType;
    }

    /** Marks participants as waiting while leaving them at their round-end locations. */
    public void holdParticipantsForNextRound(@NotNull BaseGameInstance instance,
                                             @NotNull Collection<UUID> participants) {
        for (UUID uuid : participants) {
            roundTransitionHolds.put(uuid, new RoundTransitionHold(instance, instance.getRunMode()));
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player == null) continue;
            instance.sanitizeParticipantForLobby(player, false);
        }
    }

    /** Keeps spectators attached to a completed event instance until the next round can adopt them. */
    public void holdSpectatorsForNextRound(@NotNull BaseGameInstance instance) {
        for (UUID uuid : instance.getSpectatorUniqueIds())
            spectatorTransitionHolds.put(uuid, new SpectatorTransitionHold(instance));
    }

    public boolean isWaitingForNextRound(@NotNull UUID uuid) {
        return roundTransitionHolds.containsKey(uuid);
    }

    /** Restores a reconnected participant's waiting state without moving them. */
    public boolean restoreNextRoundHold(@NotNull Player player) {
        RoundTransitionHold hold = roundTransitionHolds.get(player.getUniqueId());
        if (hold == null) return false;
        hold.instance().sanitizeParticipantForLobby(player, false);
        return true;
    }

    public void teamGameEndHandler(TeamGameEndEvent event) {
        // Participant ownership is retained through the visible settlement phase.
    }

    public void singleTeamGameEndHandler(SingleGameEndEvent event) {
        // Participant ownership is retained through the visible settlement phase.
    }

    /** Clears only mappings owned by the instance being finalized. */
    public void releaseInstanceParticipants(@NotNull BaseGameInstance instance) {
        teamStatus.entrySet().removeIf(entry -> entry.getValue() == instance);
        playerStatus.entrySet().removeIf(entry -> entry.getValue() == instance);
    }

    public void releaseInstancePlayers(@NotNull BaseGameInstance instance, @NotNull Set<UUID> players) {
        for (UUID player : players) playerStatus.remove(player, instance);
    }

    @Nullable
    public BaseGameInstance getBasePlayerArea(UUID uuid) {
        return playerStatus.get(uuid);
    }

    @Nullable
    public BaseGameInstance getPlayerSpectatorStatus(UUID uuid) {
        return playerSpectatorStatus.get(uuid);
    }

    public synchronized boolean spectateArea(@NotNull Player player, @NotNull BaseGameInstance baseArea) {
        UUID uuid = player.getUniqueId();
        if (playerSpectatorStatus.containsKey(uuid)) {
            return false;
        }
        if (playerStatus.containsKey(uuid)) {
            return false;
        }

        playerSpectatorStatus.put(uuid, baseArea);
        baseArea.addSpectator(player);
        if (plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidate(player);
        return true;
    }

    /** Opens the live arena selector used by the player-facing spectate command. */
    public void openSpectateMenu(@NotNull Player player) {
        spectateMenu.open(player);
    }

    /** Applies the same roster restriction as the explicit spectate command. Automatic routing bypasses it. */
    public boolean canManuallySpectate(@NotNull Player player) {
        if (!CCConfig.STRICT_SPECTATOR_RULE) return true;
        ChampionshipTeam team = plugin.getTeamManager().getTeamByPlayer(player);
        if (plugin.getRankManager().getRound() != 7 && team != null && !player.hasPermission("cc.refuge")) {
            player.sendMessage(ink.ziip.championshipscore.configuration.config.message.MessageConfig.SPECTATOR_IS_PLAYER);
            return false;
        }
        return true;
    }

    /** Active runtime instances shown by the spectator menu, including every PKT/BB copy. */
    public List<BaseGameInstance> getSpectatableInstances() {
        List<BaseGameInstance> instances = new ArrayList<>(areaManagers.entrySet().stream()
                .filter(entry -> isGameEnabled(entry.getKey()) && loadedGameManagers.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().getRuntimeInstances().stream()
                        .map(instance -> (BaseGameInstance) instance))
                .filter(this::isInstanceActivelyRunning)
                .toList());
        remoteBingoInstances.values().stream().filter(this::isInstanceActivelyRunning).forEach(instances::add);
        instances.sort(Comparator
                .comparingInt((BaseGameInstance instance) -> instance.getGameTypeEnum().ordinal())
                .thenComparing(instance -> instance.getGameConfig().getAreaName(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparingInt(this::spectatorInstanceIndex));
        return List.copyOf(instances);
    }

    /** Selects or switches to one live arena without an intermediate lobby teleport. */
    public synchronized boolean selectSpectatorArea(@NotNull Player player, @NotNull BaseGameInstance target) {
        if (!isInstanceActivelyRunning(target) || playerStatus.containsKey(player.getUniqueId())) return false;
        if (playerSpectatorStatus.get(player.getUniqueId()) == target) return true;
        moveSpectatorTo(player, target);
        return true;
    }

    /** Selects a live arena and teleports directly to a destination inside that same spectator instance. */
    public synchronized boolean selectSpectatorArea(@NotNull Player player, @NotNull BaseGameInstance target,
                                                    @NotNull Location destination) {
        if (!isInstanceActivelyRunning(target) || playerStatus.containsKey(player.getUniqueId())) return false;
        if (playerSpectatorStatus.get(player.getUniqueId()) == target) {
            target.teleportSpectatorAsync(player, destination);
            return true;
        }
        moveSpectatorTo(player, target, destination);
        return true;
    }

    private int spectatorInstanceIndex(@NotNull BaseGameInstance instance) {
        if (instance instanceof ParkourTagArea parkourTag) return parkourTag.getCopyIndex();
        if (instance instanceof BattleBoxArea battleBox) return battleBox.getCopyIndex();
        if (instance instanceof ink.ziip.championshipscore.api.game.acerace.AceRaceArea aceRace)
            return aceRace.getCopyIndex();
        return 0;
    }

    /** Routes an unteamed player joining mid-game to the current spectator focus. */
    public synchronized boolean spectateCurrentGame(@NotNull Player player) {
        if (plugin.getTeamManager().getTeamByPlayer(player) != null || playerStatus.containsKey(player.getUniqueId()))
            return false;
        BaseGameInstance active = getCurrentSpectatorFocus();
        return active != null && spectateArea(player, active);
    }

    /** Releases spectators attached to event-owned instances only; standalone games are untouched. */
    public synchronized void releaseEventSpectatorsForGame(@NotNull GameTypeEnum gameType) {
        List<Map.Entry<UUID, BaseGameInstance>> entries = playerSpectatorStatus.entrySet().stream()
                .filter(entry -> entry.getValue().getGameTypeEnum() == gameType
                        && (entry.getValue().isEventRun()
                        || isHeldSpectator(entry.getKey(), entry.getValue())))
                .toList();
        for (Map.Entry<UUID, BaseGameInstance> entry : entries) {
            Player player = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (player != null) entry.getValue().removeSpectator(player);
            else entry.getValue().onlyRemoveSpectatorFromList(entry.getKey());
            playerSpectatorStatus.remove(entry.getKey(), entry.getValue());
        }
        spectatorTransitionHolds.entrySet().removeIf(entry ->
                entry.getValue().instance().getGameTypeEnum() == gameType);
    }

    public void clearSpectatorStatus(@NotNull UUID uuid, @NotNull BaseGameInstance expected) {
        playerSpectatorStatus.remove(uuid, expected);
        spectatorTransitionHolds.computeIfPresent(uuid, (ignored, hold) ->
                hold.instance() == expected ? null : hold);
        if (spectatorFocus == expected && expected.getOnlineSpectators().isEmpty()
                && !isInstanceAvailableForSpectating(expected)) {
            spectatorFocus = null;
        }
    }

    private synchronized void focusSpectatorsOn(@NotNull BaseGameInstance startedInstance) {
        if (!startedInstance.isEventRun()) return;
        BaseGameInstance current = getCurrentSpectatorFocus();
        BaseGameInstance target = current != null && current.getGameTypeEnum() == startedInstance.getGameTypeEnum()
                && isInstanceActivelyRunning(current) ? current : startedInstance;
        spectatorFocus = target;
        transferHeldSpectatorsTo(target);

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (plugin.getTeamManager().getTeamByPlayer(player) != null) continue;
            UUID uuid = player.getUniqueId();
            BaseGameInstance previous = playerSpectatorStatus.get(uuid);
            if (previous == target) continue;
            moveSpectatorTo(player, target);
        }
    }

    private boolean isHeldSpectator(@NotNull UUID uuid, @NotNull BaseGameInstance instance) {
        SpectatorTransitionHold hold = spectatorTransitionHolds.get(uuid);
        return hold != null && hold.instance() == instance;
    }

    /** Transfers both online and offline spectators without a lobby hop once the next round is live. */
    private void transferHeldSpectatorsTo(@NotNull BaseGameInstance target) {
        List<Map.Entry<UUID, SpectatorTransitionHold>> holds = spectatorTransitionHolds.entrySet().stream()
                .filter(entry -> entry.getValue().instance().getGameTypeEnum() == target.getGameTypeEnum())
                .toList();
        for (Map.Entry<UUID, SpectatorTransitionHold> entry : holds) {
            UUID uuid = entry.getKey();
            SpectatorTransitionHold hold = entry.getValue();
            if (!spectatorTransitionHolds.remove(uuid, hold)) continue;

            BaseGameInstance previous = playerSpectatorStatus.get(uuid);
            if (previous != hold.instance()) continue;

            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (previous == target) {
                if (player != null && player.isOnline())
                    target.teleportSpectatorAsync(player, target.getSpectatorSpawnLocation());
                continue;
            }
            if (player != null && player.isOnline()) {
                moveSpectatorTo(player, target);
                continue;
            }
            if (playerSpectatorStatus.replace(uuid, previous, target)) {
                previous.onlyRemoveSpectatorFromList(uuid);
                target.addSpectatorWithoutTeleport(uuid);
            }
        }
    }

    private void moveSpectatorTo(@NotNull Player player, @NotNull BaseGameInstance target) {
        moveSpectatorTo(player, target, target.getSpectatorSpawnLocation());
    }

    private void moveSpectatorTo(@NotNull Player player, @NotNull BaseGameInstance target,
                                 @NotNull Location destination) {
        UUID uuid = player.getUniqueId();
        BaseGameInstance previous = playerSpectatorStatus.get(uuid);
        if (previous == target) {
            target.teleportSpectatorAsync(player, destination);
            return;
        }
        spectatorTransitionHolds.remove(uuid);
        if (previous != null) previous.detachSpectator(player);
        if (plugin.getDailyManager() != null) plugin.getDailyManager().detachSpectator(uuid);
        playerSpectatorStatus.put(uuid, target);
        target.addSpectator(player, destination);
        if (plugin.getDailyManager() != null) plugin.getDailyManager().attachSpectator(target, uuid);
    }


    @Nullable
    private BaseGameInstance getCurrentSpectatorFocus() {
        BaseGameInstance focus = spectatorFocus;
        if (isInstanceAvailableForSpectating(focus)) return focus;

        for (GameTypeEnum gameType : GameTypeEnum.values()) {
            BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameType);
            if (manager == null) continue;
            BaseGameInstance active = manager.getRuntimeInstances().stream()
                    .filter(BaseGameInstance::isEventRun)
                    .filter(this::isInstanceActivelyRunning)
                    .sorted(Comparator.comparing(instance -> instance.getGameConfig().getConfigName(),
                            String.CASE_INSENSITIVE_ORDER))
                    .findFirst().orElse(null);
            if (active != null) {
                spectatorFocus = active;
                return active;
            }
        }
        return null;
    }

    private boolean isInstanceAvailableForSpectating(@Nullable BaseGameInstance instance) {
        if (instance == null) return false;
        if (!instance.isEventRun()) return false;
        if (isInstanceActivelyRunning(instance)) return true;
        return instance.isEventRun() && instance.getGameStageEnum() == GameStageEnum.END;
    }

    private boolean isInstanceActivelyRunning(@NotNull BaseGameInstance instance) {
        return switch (instance.getGameStageEnum()) {
            case LOADING, PREPARATION, COUNTDOWN, PROGRESS -> true;
            default -> false;
        };
    }

    public boolean leaveSpectating(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (playerSpectatorStatus.containsKey(uuid)) {
            BaseGameInstance baseArea = playerSpectatorStatus.get(uuid);
            baseArea.removeSpectator(player);
            playerSpectatorStatus.remove(uuid);
            if (plugin.getDailyManager() != null) plugin.getDailyManager().detachSpectator(uuid);
            spectatorTransitionHolds.remove(uuid);
            if (plugin.getSidebarManager() != null) plugin.getSidebarManager().invalidate(player);
            return true;
        }

        return false;
    }

    public void removeSpectator(@NotNull UUID uuid) {
        if (playerSpectatorStatus.containsKey(uuid)) {
            BaseGameInstance baseArea = playerSpectatorStatus.get(uuid);
            baseArea.removeSpectator(uuid);
            playerSpectatorStatus.remove(uuid);
            if (plugin.getDailyManager() != null) plugin.getDailyManager().detachSpectator(uuid);
        }
        spectatorTransitionHolds.remove(uuid);
    }

    public void removeSpectatingPlayerFromList(@NotNull UUID uuid) {
        if (playerSpectatorStatus.containsKey(uuid)) {
            BaseGameInstance baseArea = playerSpectatorStatus.get(uuid);
            baseArea.onlyRemoveSpectatorFromList(uuid);
            playerSpectatorStatus.remove(uuid);
            if (plugin.getDailyManager() != null) plugin.getDailyManager().detachSpectator(uuid);
        }
        spectatorTransitionHolds.remove(uuid);
    }

    private record RoundTransitionHold(BaseGameInstance instance, GameRunMode mode) {
   }

    private record SpectatorTransitionHold(BaseGameInstance instance) {
    }
}

package ink.ziip.championshipscore.api.game.manager;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.BaseManager;
import ink.ziip.championshipscore.api.event.SingleGameEndEvent;
import ink.ziip.championshipscore.api.event.TeamGameEndEvent;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxArea;
import ink.ziip.championshipscore.api.game.battlebox.BattleBoxManager;
import ink.ziip.championshipscore.api.game.bingo.BingoManager;
import ink.ziip.championshipscore.api.game.buildmart.BuildMartManager;
import ink.ziip.championshipscore.api.game.decarnival.DragonEggCarnivalManager;
import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltManager;
import ink.ziip.championshipscore.api.game.dodgebolt.DodgeboltArea;
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
import ink.ziip.championshipscore.util.scheduler.FoliaScheduler;
import lombok.Getter;
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
    @Getter
    private final DodgeboltManager dodgeboltManager;
    /**
     * Registry mapping each game type to its area manager. Drives the generic
     * {@code join*} dispatch so adding a game only requires registering it here.
     */
    private final Map<GameTypeEnum, BaseGameInstanceManager<? extends BaseGameInstance>> areaManagers = new EnumMap<>(GameTypeEnum.class);
    /** Lazily parsed from {@link CCConfig#ENABLED_GAMES}; see {@link #getEnabledGames()}. */
    private volatile Set<GameTypeEnum> enabledGames;
    /** Managers that have actually been loaded, including disabled games opened through map editing. */
    private final Set<GameTypeEnum> loadedGameManagers = ConcurrentHashMap.newKeySet();

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
        dodgeboltManager = new DodgeboltManager(plugin);

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
    public synchronized Set<GameTypeEnum> getEnabledGames() {
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
            enabledGames = Set.copyOf(parsed);
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
    }

    @Override
    public void unload() {
        for (GameTypeEnum gameType : EnumSet.copyOf(loadedGameManagers)) {
            BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameType);
            if (manager != null) {
                if (plugin.isEnabled()) manager.unload();
                else manager.clearAreas();
            }
        }
        loadedGameManagers.clear();
        enabledGames = null;

        gameManagerHandler.unRegister();
    }

    /** Unloads runtime objects for config reload without racing asynchronous world unloads. */
    public void unloadForReload() {
        for (GameTypeEnum gameType : EnumSet.copyOf(loadedGameManagers)) {
            BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameType);
            if (manager != null) manager.retainManagedWorldsOnNextClear();
        }
        unload();
    }

    public boolean hasRunningAreas() {
        for (GameTypeEnum gameType : EnumSet.copyOf(loadedGameManagers)) {
            BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameType);
            if (manager == null) continue;
            for (BaseGameInstance instance : manager.getRuntimeInstances()) {
                if (instance.getGameStageEnum() != GameStageEnum.WAITING) return true;
            }
        }
        return false;
    }

    /**
     * Force-ends every currently-running area of the given game (any area not in WAITING). Used by the
     * schedule "delete current game" flow to scrap a broken/in-progress game before clearing its records.
     * Calls {@link BaseGameInstance#endGameFinally()}, which removes players and resets the instance.
     */
    public void forceEndAreas(@NotNull GameTypeEnum gameTypeEnum) {
        BaseGameInstanceManager<?> manager = areaManagers.get(gameTypeEnum);
        if (manager == null) return;
        for (BaseGameInstance instance : manager.getRuntimeInstances()) {
            if (instance.getGameStageEnum() != GameStageEnum.WAITING) {
                instance.endGameFinally();
            }
        }
    }

    public boolean joinTeamArea(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area, @NotNull ChampionshipTeam rightChampionshipTeam, @NotNull ChampionshipTeam leftChampionshipTeam) {
        return joinTeamArea(gameTypeEnum, area, rightChampionshipTeam, leftChampionshipTeam, true);
    }

    /** Starts the non-scoring final and records which finalist owns both opening arrows. */
    public boolean joinDodgeboltArea(@NotNull String area, @NotNull ChampionshipTeam rightTeam,
                                     @NotNull ChampionshipTeam leftTeam,
                                     @NotNull ChampionshipTeam higherSeed, boolean showIntroduction) {
        DodgeboltArea instance = dodgeboltManager.getArea(area);
        if (instance == null || (!higherSeed.equals(rightTeam) && !higherSeed.equals(leftTeam))) return false;
        instance.setFirstRoundArrowTeam(higherSeed);
        boolean started = joinTeamArea(GameTypeEnum.Dodgebolt, area, rightTeam, leftTeam, showIntroduction);
        if (!started) instance.setFirstRoundArrowTeam(null);
        return started;
    }

    /** Moves every online non-finalist into the final's spectator set without strict-spectator checks. */
    public void spectateDodgeboltFinal(@NotNull DodgeboltArea area,
                                       @NotNull ChampionshipTeam rightTeam,
                                       @NotNull ChampionshipTeam leftTeam) {
        FoliaScheduler.global(plugin).runTask(() -> {
            for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (rightTeam.isTeamMember(player) || leftTeam.isTeamMember(player)) continue;
                if (playerSpectatorStatus.containsKey(player.getUniqueId())) leaveSpectating(player);
                spectateArea(player, area);
            }
        });
    }

    public boolean joinTeamArea(@NotNull GameTypeEnum gameTypeEnum, @NotNull String area,
                                @NotNull ChampionshipTeam rightChampionshipTeam,
                                @NotNull ChampionshipTeam leftChampionshipTeam, boolean showIntroduction) {
        if (!isGameEnabled(gameTypeEnum))
            return false;
        if (!plugin.getPrepareSessionManager().canStart(gameTypeEnum, area))
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

        BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameTypeEnum);
        if (manager == null)
            return false;
        if (!(manager.getArea(area) instanceof BasePairedGameInstance teamArea))
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
        if (!plugin.getPrepareSessionManager().canStart(gameTypeEnum, area))
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

        BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameTypeEnum);
        if (manager == null)
            return false;
        if (!(manager.getArea(area) instanceof BaseMultiTeamGameInstance singleTeamArea))
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
        if (!plugin.getPrepareSessionManager().canStart(gameTypeEnum, area))
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

        BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameTypeEnum);
        if (manager == null)
            return false;
        if (!(manager.getArea(area) instanceof BaseMultiTeamGameInstance singleTeamArea))
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
        if (!plugin.getPrepareSessionManager().canStart(gameTypeEnum, area))
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

        BaseGameInstanceManager<? extends BaseGameInstance> manager = areaManagers.get(gameTypeEnum);
        if (manager == null)
            return false;
        if (!(manager.getArea(area) instanceof BaseMultiTeamGameInstance singleTeamArea))
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

    /** Starts one or more independent Battle Box instances from a shared map definition. */
    public synchronized boolean joinBattleBoxArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs) {
        return joinBattleBoxArea(area, pairs, true);
    }

    public synchronized boolean joinBattleBoxArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs,
                                                   boolean showIntroduction) {
        return joinBattleBoxInstances(area, pairs, showIntroduction) != null;
    }

    /** Returns the exact instances started for round-completion tracking, or {@code null} on failure. */
    public synchronized @Nullable List<BattleBoxArea> joinBattleBoxInstances(
            @NotNull String area, @NotNull List<TwoVTwoVector> pairs, boolean showIntroduction) {
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
                if (playerStatus.containsKey(uuid))
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
            instance.setIntroductionEnabledForNextStart(showIntroduction);
            if (!instance.tryStartGame(pair.getTeamOne(), pair.getTeamTwo())) {
                startGate.complete(null);
                instance.setIntroductionEnabledForNextStart(true);
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
                .whenComplete((unused, error) -> FoliaScheduler.global(plugin)
                        .runTask(() -> startGate.complete(null)));
        return List.copyOf(selected);
    }

    /** Battle-Box-style parallel start for Parkour Tag: each pairing runs in its own stamped arena copy. */
    public synchronized boolean joinParkourTagArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs) {
        return joinParkourTagArea(area, pairs, true);
    }

    public synchronized boolean joinParkourTagArea(@NotNull String area, @NotNull List<TwoVTwoVector> pairs,
                                                    boolean showIntroduction) {
        return joinParkourTagInstances(area, pairs, showIntroduction) != null;
    }

    /** Returns the exact instances started for round-completion tracking, or {@code null} on failure. */
    public synchronized @Nullable List<ParkourTagArea> joinParkourTagInstances(
            @NotNull String area, @NotNull List<TwoVTwoVector> pairs, boolean showIntroduction) {
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
                if (playerStatus.containsKey(uuid))
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
            instance.setIntroductionEnabledForNextStart(showIntroduction);
            if (!instance.tryStartGame(pair.getTeamOne(), pair.getTeamTwo())) {
                startGate.complete(null);
                instance.setIntroductionEnabledForNextStart(true);
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
                .whenComplete((unused, error) -> FoliaScheduler.global(plugin)
                        .runTask(() -> startGate.complete(null)));
        return List.copyOf(selected);
    }

    public String getPlayerCurrentAreaName(UUID uuid) {
        BaseGameInstance baseArea = playerStatus.get(uuid);

        if (baseArea != null)
            return baseArea.getGameConfig().getConfigName();

        baseArea = playerSpectatorStatus.get(uuid);

        if (baseArea != null)
            return baseArea.getGameConfig().getConfigName();

        return "";
    }

    public BaseGameInstance getTeamCurrenArea(ChampionshipTeam championshipTeam) {
        return teamStatus.get(championshipTeam);
    }

    private void addPlayerStatusByTeam(ChampionshipTeam championshipTeam, BaseGameInstance baseArea) {
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
        return true;
    }

    public boolean leaveSpectating(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (playerSpectatorStatus.containsKey(uuid)) {
            BaseGameInstance baseArea = playerSpectatorStatus.get(uuid);
            baseArea.removeSpectator(player);
            playerSpectatorStatus.remove(uuid);
            return true;
        }

        return false;
    }

    public void removeSpectator(@NotNull UUID uuid) {
        if (playerSpectatorStatus.containsKey(uuid)) {
            BaseGameInstance baseArea = playerSpectatorStatus.get(uuid);
            baseArea.removeSpectator(uuid);
            playerSpectatorStatus.remove(uuid);
        }
    }

    public void removeSpectatingPlayerFromList(@NotNull UUID uuid) {
        if (playerSpectatorStatus.containsKey(uuid)) {
            BaseGameInstance baseArea = playerSpectatorStatus.get(uuid);
            baseArea.onlyRemoveSpectatorFromList(uuid);
            playerSpectatorStatus.remove(uuid);
        }
    }
}

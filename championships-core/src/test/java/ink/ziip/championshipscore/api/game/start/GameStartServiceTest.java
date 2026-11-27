package ink.ziip.championshipscore.api.game.start;

import static ink.ziip.championshipscore.support.TestObjects.allocate;
import static ink.ziip.championshipscore.support.TestObjects.field;
import static ink.ziip.championshipscore.support.TestObjects.proxy;
import static ink.ziip.championshipscore.support.TestObjects.set;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.acerace.AceRaceManager;
import ink.ziip.championshipscore.api.game.acerace.config.AceRaceConfig;
import ink.ziip.championshipscore.api.game.acerace.model.AceRaceCourse;
import ink.ziip.championshipscore.api.game.acerace.runtime.AceRaceArea;
import ink.ziip.championshipscore.api.game.area.prepare.PrepareSessionManager;
import ink.ziip.championshipscore.api.game.config.BaseGameConfig;
import ink.ziip.championshipscore.api.game.frostbite.FrostbiteManager;
import ink.ziip.championshipscore.api.game.frostbite.config.FrostbiteConfig;
import ink.ziip.championshipscore.api.game.frostbite.runtime.FrostbiteArea;
import ink.ziip.championshipscore.api.game.hotycodydusky.config.HotyCodyDuskyConfig;
import ink.ziip.championshipscore.api.game.hotycodydusky.runtime.HotyCodyDuskyTeamArea;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.instance.multiteam.BaseMultiTeamGameInstance;
import ink.ziip.championshipscore.api.game.instance.paired.BasePairedGameInstance;
import ink.ziip.championshipscore.api.game.lasershowdown.runtime.LaserShowdownTeamArea;
import ink.ziip.championshipscore.api.game.manager.*;
import ink.ziip.championshipscore.api.game.model.*;
import ink.ziip.championshipscore.api.game.riptiderush.RiptideRushManager;
import ink.ziip.championshipscore.api.game.riptiderush.config.RiptideRushConfig;
import ink.ziip.championshipscore.api.game.riptiderush.runtime.RiptideRushArea;
import ink.ziip.championshipscore.api.game.tntrun.config.TNTRunConfig;
import ink.ziip.championshipscore.api.game.tntrun.runtime.TNTRunTeamArea;
import ink.ziip.championshipscore.api.schedule.FormalEventMapResolver;
import ink.ziip.championshipscore.api.schedule.ScheduleManager;
import ink.ziip.championshipscore.api.schedule.model.TwoVTwoVector;
import ink.ziip.championshipscore.api.team.*;
import ink.ziip.championshipscore.api.visibility.PlayerVisibilityManager;
import ink.ziip.championshipscore.configuration.config.message.GameMessages;
import ink.ziip.championshipscore.configuration.manager.ConfigurationManager;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

@org.junit.jupiter.api.extension.ExtendWith(
        ink.ziip.championshipscore.configuration.ConfigurationStateExtension.class)
class GameStartServiceTest {
    @org.junit.jupiter.api.BeforeEach
    void enableGameTestsForFixture() {
        ink.ziip.championshipscore.configuration.config.CCConfig.TEST_ENABLED = true;
    }

    @org.junit.jupiter.api.Test
    void disabledTestsCannotBypassTheSessionThroughTheSharedStartSurface() {
        ink.ziip.championshipscore.configuration.config.CCConfig.TEST_ENABLED = false;
        var service = new GameStartService(null);
        for (var mode : List.of(GameRunMode.TEST, GameRunMode.TEST_EVENT)) {
            var result =
                    service.startManual(
                                    new GameStartArguments(
                                            GameTypeEnum.Bingo,
                                            "bingo",
                                            List.of(),
                                            ArenaSelection.all()),
                                    mode,
                                    List.of())
                            .toCompletableFuture()
                            .join();
            assertFalse(result.started());
            assertTrue(result.detail().contains("disabled"));
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = GameRunMode.class,
            names = {"GAME", "EVENT", "DAILY"})
    void disabledTestGatePreservesProductionStartModes(GameRunMode mode) throws Exception {
        ink.ziip.championshipscore.configuration.config.CCConfig.TEST_ENABLED = false;
        withFixture(
                GameTypeEnum.TNTRun,
                f -> {
                    var arguments =
                            GameStartArguments.parse(
                                    new String[] {"tntrun", "CHOSEN", "#2", "team-1"}, false);
                    var result =
                            new GameStartService(f.plugin)
                                    .startManual(arguments, mode)
                                    .toCompletableFuture()
                                    .join();
                    assertTrue(result.started(), result.detail());
                    assertEquals(mode, f.manager.mode);
                    assertEquals(List.of(f.teams.get(1), f.teams.get(0)), f.manager.requested);
                });
    }

    @ParameterizedTest
    @EnumSource(GameTypeEnum.class)
    void everyGameCarriesTheChosenMapAndOnlyTheExplicitTeams(GameTypeEnum game) throws Exception {
        withFixture(
                game,
                f -> {
                    String arena =
                            game == GameTypeEnum.IAmNotARobot
                                    ? "3,1"
                                    : GameStartRules.internalArenas(game) ? "3" : "1";
                    var arguments =
                            GameStartArguments.parse(
                                    new String[] {
                                        game.commandName(),
                                        "CHOSEN",
                                        "#2",
                                        "team-1",
                                        "--arena",
                                        arena
                                    },
                                    false);
                    var result =
                            new GameStartService(f.plugin)
                                    .startManual(arguments)
                                    .toCompletableFuture()
                                    .join();
                    assertTrue(result.started(), result.detail());
                    assertEquals("Chosen", f.manager.map);
                    assertEquals(List.of(f.teams.get(1), f.teams.get(0)), f.manager.requested);
                    assertFalse(f.manager.requested.contains(f.teams.get(2)));
                    assertEquals(GameRunMode.GAME, f.manager.mode);
                    if (GameStartRules.internalArenas(game))
                        assertEquals(
                                game == GameTypeEnum.IAmNotARobot ? List.of(2, 0) : List.of(2),
                                f.area.getSelectedArenaIndices(4));
                });
    }

    @ParameterizedTest
    @EnumSource(
            value = GameTypeEnum.class,
            names = {"Dodgebolt", "DragonEggCarnival", "SulfurSoccer"},
            mode = EnumSource.Mode.EXCLUDE)
    void formalSelectionPersistsAndInvalidSubarenaNeverDispatches(GameTypeEnum game)
            throws Exception {
        withFixture(
                game,
                f -> {
                    var schedule = new RecordingSchedule(f.plugin);
                    set(f.plugin, "scheduleManager", schedule);
                    var arguments =
                            GameStartArguments.parse(
                                    new String[] {game.commandName(), "Chosen", "#2", "team-1"},
                                    true);
                    assertEquals(
                            ScheduleManager.EventAction.STARTED,
                            schedule.startOrStopFormalEvent(arguments));
                    assertEquals(
                            List.of(f.teams.get(1), f.teams.get(0)),
                            schedule.participatingTeams(game));
                    assertEquals("Chosen", schedule.selectedMap(game));
                    assertEquals(
                            List.of("Chosen", "Chosen"),
                            List.of(
                                    ink.ziip.championshipscore.api.schedule.FormalEventMapResolver
                                            .map(f.plugin, game, 1),
                                    ink.ziip.championshipscore.api.schedule.FormalEventMapResolver
                                            .map(f.plugin, game, 3)));
                    schedule.clearStartSelection(game);
                    schedule.running = false;
                    var invalid =
                            GameStartArguments.parse(
                                    new String[] {
                                        game.commandName(),
                                        "Chosen",
                                        "#2",
                                        "team-1",
                                        "--arena",
                                        "99"
                                    },
                                    true);
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> schedule.startOrStopFormalEvent(invalid));
                    assertNull(schedule.selectedMap(game));
                    assertEquals(1, schedule.dispatches);
                });
    }

    @Test
    void registeredFishMapOutsideTheFormalPoolCanStillStartManually() throws Exception {
        var game = GameTypeEnum.HotyCodyDusky;
        withFixture(
                game,
                f -> {
                    var schedule = new RecordingSchedule(f.plugin);
                    set(f.plugin, "scheduleManager", schedule);
                    var config =
                            f.plugin.getConfigurationManager().getCCConfig().getConfiguration();
                    config.set("formal-events.HotyCodyDusky.maps", List.of("codusk_basin"));
                    var arguments =
                            GameStartArguments.parse(
                                    new String[] {game.commandName(), "Chosen", "#2", "team-1"},
                                    true);
                    assertEquals(
                            List.of("codusk_basin"), FormalEventMapResolver.maps(f.plugin, game));
                    assertTrue(FormalEventMapResolver.selectableMaps(f.plugin, game).isEmpty());
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> schedule.startOrStopFormalEvent(arguments));
                    assertEquals(0, schedule.dispatches);
                    assertNull(schedule.selectedMap(game));
                    assertTrue(
                            new GameStartService(f.plugin)
                                    .startManual(arguments)
                                    .toCompletableFuture()
                                    .join()
                                    .started());
                    config.set("formal-events.HotyCodyDusky.maps", List.of());
                    assertTrue(FormalEventMapResolver.maps(f.plugin, game).isEmpty());
                });
    }

    @Test
    void bingoExecutionFailureReturnsACompletedRejection() throws Exception {
        withFixture(
                GameTypeEnum.Bingo,
                f -> {
                    f.manager.failBingo = true;
                    var result =
                            new GameStartService(f.plugin)
                                    .startManual(
                                            GameStartArguments.parse(
                                                    new String[] {"bingo", "Chosen", "#1", "#2"},
                                                    false))
                                    .toCompletableFuture()
                                    .join();
                    assertFalse(result.started());
                    assertTrue(result.detail().contains("失败"));
                });
    }

    private static final String ROSTER_MAP = "frosty_fjord";
    private static final UUID ADMIN = new UUID(0, 1);
    private static final UUID RED_ONLINE = new UUID(1, 1);
    private static final UUID RED_OFFLINE = new UUID(1, 2);
    private static final UUID BLUE_ONLINE = new UUID(2, 1);
    private static final UUID BLUE_OFFLINE = new UUID(2, 2);
    private static final Set<UUID> ROSTER =
            Set.of(RED_ONLINE, RED_OFFLINE, BLUE_ONLINE, BLUE_OFFLINE);

    /**
     * Uses the real {@link GameManager} admission path: outsiders are rejected before any match
     * ownership changes and {@code all} enrolls every team member, including offline ones.
     */
    @ParameterizedTest
    @EnumSource(
            value = GameTypeEnum.class,
            names = {"FrostbiteFrenzy", "RiptideRush", "AceRace"})
    void allTeamsStartEnrollsOfflineMembersAndRejectsOutsiders(GameTypeEnum game) throws Exception {
        Field serverField = field(Bukkit.class, "server");
        Object previousServer = serverField.get(null);
        Field instanceField = field(ChampionshipsCore.class, "instance");
        Object previousInstance = instanceField.get(null);
        try {
            Map<UUID, Player> online =
                    Map.of(RED_ONLINE, player(RED_ONLINE), BLUE_ONLINE, player(BLUE_ONLINE));
            BukkitTask task = proxy(BukkitTask.class, (p, m, a) -> null);
            BukkitScheduler scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (p, m, a) -> {
                                if (m.getName().equals("runTask")) return task;
                                throw new AssertionError(
                                        "Unexpected scheduler call: " + m.getName());
                            });
            serverField.set(
                    null,
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getPlayer" -> online.get(a[0]);
                                        case "isPrimaryThread" -> false;
                                        case "getScheduler" -> scheduler;
                                        default ->
                                                throw new AssertionError(
                                                        "Unexpected server call: " + m.getName());
                                    }));

            ChampionshipsCore plugin = allocate(ChampionshipsCore.class);
            instanceField.set(null, plugin);
            GameManager manager = allocate(GameManager.class);
            set(manager, "plugin", plugin);
            set(manager, "enabledGames", Set.of(game));
            for (String name :
                    List.of(
                            "teamStatus",
                            "playerStatus",
                            "playerSpectatorStatus",
                            "roundTransitionHolds",
                            "spectatorTransitionHolds"))
                set(manager, name, new ConcurrentHashMap<>());
            set(plugin, "gameManager", manager);
            set(plugin, "visibilityManager", new PlayerVisibilityManager(plugin));

            ChampionshipTeam red = new RosterTeam(1, "red", Set.of(RED_ONLINE, RED_OFFLINE));
            ChampionshipTeam blue = new RosterTeam(2, "blue", Set.of(BLUE_ONLINE, BLUE_OFFLINE));
            TeamManager teamManager = allocate(TeamManager.class);
            set(
                    teamManager,
                    "cachedTeams",
                    new ConcurrentHashMap<>(Map.of("red", red, "blue", blue)));
            set(teamManager, "pendingMemberTeamIds", Set.of());
            set(teamManager, "pendingTeamDeletions", Set.of());
            set(plugin, "teamManager", teamManager);

            BaseGameConfig config =
                    switch (game) {
                        case FrostbiteFrenzy -> new FrostbiteConfig(plugin, ROSTER_MAP);
                        case RiptideRush -> new RiptideRushConfig(plugin, ROSTER_MAP);
                        case AceRace -> new AceRaceConfig(plugin, ROSTER_MAP);
                        default -> throw new AssertionError(game);
                    };
            set(config, "preparePublished", true);
            set(config, "prepareDirty", false);
            BaseMultiTeamGameInstance area =
                    switch (game) {
                        case FrostbiteFrenzy -> allocate(FrostbiteArea.class);
                        case RiptideRush -> allocate(RiptideRushArea.class);
                        case AceRace -> allocate(AceRaceArea.class);
                        default -> throw new AssertionError(game);
                    };
            if (game == GameTypeEnum.AceRace) set(area, "course", AceRaceCourse.EMPTY);
            set(area, "plugin", plugin);
            set(area, "scheduler", scheduler);
            set(area, "gameConfig", config);
            set(area, "gameTypeEnum", game);
            set(area, "gameStageEnum", GameStageEnum.WAITING);
            set(area, "gameTeams", new ArrayList<ChampionshipTeam>());
            set(area, "gamePlayers", new ArrayList<UUID>());
            set(area, "startChunkTickets", new HashSet<>());
            BaseGameInstanceManager<?> gameAreas =
                    switch (game) {
                        case FrostbiteFrenzy -> new FrostbiteManager(plugin);
                        case RiptideRush -> new RiptideRushManager(plugin);
                        case AceRace -> new AceRaceManager(plugin);
                        default -> throw new AssertionError(game);
                    };
            if (game == GameTypeEnum.AceRace)
                set(
                        gameAreas,
                        "instancesByMap",
                        new ConcurrentHashMap<>(Map.of(ROSTER_MAP, List.of(area))));
            @SuppressWarnings("unchecked")
            Map<String, BaseMultiTeamGameInstance> maps =
                    (Map<String, BaseMultiTeamGameInstance>)
                            field(BaseGameInstanceManager.class, "areas").get(gameAreas);
            maps.put(ROSTER_MAP, area);
            set(manager, "areaManagers", Map.of(game, gameAreas));
            PrepareSessionManager prepare = allocate(PrepareSessionManager.class);
            set(prepare, "plugin", plugin);
            set(prepare, "mapLocks", Map.of());
            set(plugin, "prepareSessionManager", prepare);

            // Reject outsiders before detaching spectators or changing any match ownership.
            set(manager, "playerSpectatorStatus", new ConcurrentHashMap<>(Map.of(ADMIN, area)));
            assertFalse(manager.joinSingleTeamAreaForAllTeams(game, ROSTER_MAP, List.of(ADMIN)));
            assertFalse(area.tryStartGame(List.of(red, blue), List.of(RED_ONLINE, ADMIN)));
            assertEquals(GameStageEnum.WAITING, area.getGameStageEnum());
            assertTrue(area.getParticipantUniqueIds().isEmpty());
            assertNull(manager.getBasePlayerArea(ADMIN));
            assertSame(area, manager.getPlayerSpectatorStatus(ADMIN));

            GameMessages.GAME_SINGLE_GAME_START_SUCCESSFUL = "started %game% %area%";
            GameMessages.GAME_SINGLE_GAME_START_FAILED = "failed %game% %area%";
            GameMessages.GAME_FROSTBITE = "Frostbite";
            GameMessages.GAME_RIPTIDE_RUSH = "RiptideRush";
            GameMessages.GAME_ACE_RACE = "AceRace";
            var result =
                    new GameStartService(plugin)
                            .startManual(
                                    GameStartArguments.parse(
                                            new String[] {game.commandName(), ROSTER_MAP, "all"},
                                            false))
                            .toCompletableFuture()
                            .join();
            assertTrue(result.started(), result.detail());
            assertEquals(GameStageEnum.LOADING, area.getGameStageEnum());
            assertEquals(ROSTER, Set.copyOf(area.getParticipantUniqueIds()));
            assertEquals(Set.of(red, blue), Set.copyOf(area.getGameTeams()));
            for (UUID id : ROSTER) assertSame(area, manager.getBasePlayerArea(id));
            assertNull(manager.getBasePlayerArea(ADMIN));
            assertSame(area, manager.getPlayerSpectatorStatus(ADMIN));
        } finally {
            serverField.set(null, previousServer);
            instanceField.set(null, previousInstance);
        }
    }

    private static Player player(UUID id) {
        return proxy(
                Player.class,
                (p, m, a) ->
                        switch (m.getName()) {
                            case "getUniqueId" -> id;
                            case "sendMessage" -> null;
                            default ->
                                    throw new AssertionError(
                                            "Unexpected player call: " + m.getName());
                        });
    }

    private static final class RosterTeam extends ChampionshipTeam {
        RosterTeam(int id, String name, Set<UUID> members) {
            super(id, name, name, "#FFFFFF", members, null);
        }
    }

    private record Fixture(
            ChampionshipsCore plugin,
            RecordingManager manager,
            BaseGameInstance area,
            List<ChampionshipTeam> teams) {}

    @Test
    void remoteBingoStartReadsPublishedConfigWithoutALocalArea() throws Exception {
        withFixture(
                GameTypeEnum.Bingo,
                f -> {
                    var remote = allocate(RemoteBingo.class);
                    var config =
                            new ink.ziip.championshipscore.api.game.bingo.config.BingoConfig(
                                    f.plugin, "Chosen");
                    set(remote, "remoteAreaConfigs", Map.of("Chosen", config));
                    set(remote, "areas", new ConcurrentHashMap<>());
                    set(f.manager, "areaManagers", Map.of(GameTypeEnum.Bingo, remote));
                    var prepare = allocate(PrepareSessionManager.class);
                    set(prepare, "plugin", f.plugin);
                    set(prepare, "mapLocks", Map.of());
                    set(config, "preparePublished", true);
                    set(config, "prepareDirty", false);
                    assertTrue(prepare.canStart(GameTypeEnum.Bingo, "Chosen"));
                    assertFalse(prepare.canStart(GameTypeEnum.Bingo, "missing"));
                    set(config, "prepareDirty", true);
                    assertFalse(prepare.canStart(GameTypeEnum.Bingo, "Chosen"));
                    set(config, "prepareDirty", false);
                    set(config, "preparePublished", false);
                    assertFalse(prepare.canStart(GameTypeEnum.Bingo, "Chosen"));
                    set(config, "preparePublished", true);
                    set(
                            prepare,
                            "mapLocks",
                            Map.of(GameTypeEnum.Bingo.name() + "\u0000chosen", UUID.randomUUID()));
                    assertFalse(prepare.canStart(GameTypeEnum.Bingo, "Chosen"));
                });
    }

    @ParameterizedTest
    @EnumSource(GameRunMode.class)
    void unpublishedRemoteBingoIsAdmittedOnlyForTests(GameRunMode mode) throws Exception {
        withFixture(
                GameTypeEnum.Bingo,
                f -> {
                    var prepare = allocate(UnreadyPrepare.class);
                    set(prepare, "plugin", f.plugin);
                    set(f.plugin, "prepareSessionManager", prepare);
                    set(f.manager, "bingoManager", allocate(RemoteBingo.class));
                    var arguments =
                            GameStartArguments.parse(
                                    new String[] {"bingo", "Chosen", "#1", "#2"}, false);
                    var service = new GameStartService(f.plugin);
                    boolean test = mode == GameRunMode.TEST || mode == GameRunMode.TEST_EVENT;
                    assertEquals(
                            test,
                            service.startManual(arguments, mode)
                                    .toCompletableFuture()
                                    .join()
                                    .started());
                    assertEquals(test ? "Chosen" : null, f.manager.map);
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    service.validateMap(
                                            GameTypeEnum.Bingo,
                                            "Chosen",
                                            f.teams.subList(0, 2),
                                            ArenaSelection.all()));
                    // Auto map selection must use the same gate as an explicit map.
                    assertEquals(
                            test,
                            service.startManual(
                                            GameStartArguments.parse(
                                                    new String[] {"bingo", "auto", "#1", "#2"},
                                                    false),
                                            mode)
                                    .toCompletableFuture()
                                    .join()
                                    .started());
                });
    }

    @ParameterizedTest
    @EnumSource(GameRunMode.class)
    void unpublishedLocalBingoIsRejectedInEveryMode(GameRunMode mode) throws Exception {
        withFixture(
                GameTypeEnum.Bingo,
                f -> {
                    var prepare = allocate(UnreadyPrepare.class);
                    set(prepare, "plugin", f.plugin);
                    set(f.plugin, "prepareSessionManager", prepare);
                    var result =
                            new GameStartService(f.plugin)
                                    .startManual(
                                            GameStartArguments.parse(
                                                    new String[] {"bingo", "Chosen", "#1", "#2"},
                                                    false),
                                            mode)
                                    .toCompletableFuture()
                                    .join();
                    assertFalse(result.started());
                    assertNull(f.manager.map);
                });
    }

    private static final class RemoteBingo
            extends ink.ziip.championshipscore.api.game.bingo.BingoManager {
        private RemoteBingo() {
            super(null);
        }

        @Override
        public boolean remoteExecutionConfigured() {
            return true;
        }
    }

    private static final class UnreadyPrepare extends PrepareSessionManager {
        private UnreadyPrepare() {
            super(null);
        }

        @Override
        public boolean canStart(GameTypeEnum game, String map) {
            return false;
        }
    }

    private interface Action {
        void run(Fixture f) throws Exception;
    }

    private static void withFixture(GameTypeEnum game, Action action) throws Exception {
        var sf = field(Bukkit.class, "server");
        Object previous = sf.get(null);
        try {
            Server server =
                    (Server)
                            Proxy.newProxyInstance(
                                    Server.class.getClassLoader(),
                                    new Class<?>[] {Server.class},
                                    (p, m, a) ->
                                            switch (m.getName()) {
                                                case "getPlayer" -> null;
                                                case "getScheduler" ->
                                                        Proxy.newProxyInstance(
                                                                BukkitScheduler.class
                                                                        .getClassLoader(),
                                                                new Class<?>[] {
                                                                    BukkitScheduler.class
                                                                },
                                                                (p2, m2, a2) -> null);
                                                default -> throw new AssertionError(m.getName());
                                            });
            sf.set(null, server);
            var plugin = allocate(ChampionshipsCore.class);
            set(plugin, "server", server);
            var manager = allocate(RecordingManager.class);
            set(manager, "plugin", plugin);
            set(manager, "enabledGames", Set.of(game));
            for (String name :
                    List.of(
                            "teamStatus",
                            "playerStatus",
                            "playerSpectatorStatus",
                            "roundTransitionHolds",
                            "spectatorTransitionHolds"))
                set(manager, name, new ConcurrentHashMap<>());
            set(plugin, "gameManager", manager);
            List<ChampionshipTeam> teams =
                    List.of(new TestTeam(1, game), new TestTeam(2, game), new TestTeam(3, game));
            var teamManager = allocate(TeamManager.class);
            set(
                    teamManager,
                    "cachedTeams",
                    new ConcurrentHashMap<>(
                            Map.of(
                                    "team-1",
                                    teams.get(0),
                                    "team-2",
                                    teams.get(1),
                                    "team-3",
                                    teams.get(2))));
            set(teamManager, "pendingMemberTeamIds", Set.of());
            set(teamManager, "pendingTeamDeletions", Set.of());
            set(plugin, "teamManager", teamManager);
            BaseGameInstance area;
            BaseGameConfig config;
            switch (game) {
                case IAmNotARobot -> {
                    area =
                            allocate(
                                    ink.ziip.championshipscore.api.game.iamnotarobot.runtime
                                            .RobotArea.class);
                    var c =
                            new ink.ziip.championshipscore.api.game.iamnotarobot.config.RobotConfig(
                                    plugin, "Chosen");
                    c.setLanes(4);
                    config = c;
                }
                case TNTRun -> {
                    area = allocate(TNTRunTeamArea.class);
                    var c = new TNTRunConfig(plugin, "Chosen");
                    set(c, "spawnPoints", List.of("1", "2", "3", "4"));
                    config = c;
                }
                case HotyCodyDusky -> {
                    var configs = new ConfigurationManager(plugin);
                    var document = new YamlConfiguration();
                    document.set("formal-events.HotyCodyDusky.maps", List.of("chosen"));
                    set(configs.getCCConfig(), "configuration", document);
                    set(plugin, "configurationManager", configs);
                    area = allocate(HotyCodyDuskyTeamArea.class);
                    var c = new HotyCodyDuskyConfig(plugin, "Chosen");
                    c.setCopies(4);
                    config = c;
                }
                case LaserShowdown -> {
                    area = allocate(LaserShowdownTeamArea.class);
                    set(area, "areaLocations", new java.util.ArrayList<>());
                    var c =
                            new ink.ziip.championshipscore.api.game.lasershowdown.config
                                    .LaserShowdownConfig(plugin, "Chosen");
                    var document = new org.bukkit.configuration.file.YamlConfiguration();
                    var spawns = document.createSection("player-spawn-points");
                    for (String arena : List.of("area1", "area2", "area3", "area4"))
                        spawns.set(arena, List.of());
                    c.setPlayerSpawnPoints(spawns);
                    config = c;
                }
                case FrostbiteFrenzy -> {
                    area = allocate(FrostbiteArea.class);
                    config =
                            new ink.ziip.championshipscore.api.game.frostbite.config
                                    .FrostbiteConfig(plugin, "Chosen");
                }
                default -> {
                    area = allocate(RiptideRushArea.class);
                    config = new RiptideRushConfig(plugin, "Chosen");
                }
            }
            set(area, "gameConfig", config);
            set(area, "gameStageEnum", GameStageEnum.WAITING);
            set(area, "gameTypeEnum", game);
            var maps = new Maps(plugin);
            maps.register(area);
            set(manager, "areaManagers", Map.of(game, maps));
            var prepare = allocate(ReadyPrepare.class);
            set(prepare, "plugin", plugin);
            set(plugin, "prepareSessionManager", prepare);
            action.run(new Fixture(plugin, manager, area, teams));
        } finally {
            sf.set(null, previous);
        }
    }

    private static final class RecordingSchedule extends ScheduleManager {
        boolean running;
        int dispatches;

        RecordingSchedule(ChampionshipsCore p) {
            super(p);
        }

        @Override
        public boolean isFormalEventRunning(GameTypeEnum g) {
            return running;
        }

        @Override
        public EventAction startOrStopFormalEvent(GameTypeEnum g) {
            dispatches++;
            running = !running;
            return running ? EventAction.STARTED : EventAction.STOPPED;
        }
    }

    private static final class RecordingManager extends GameManager {
        String map;
        List<ChampionshipTeam> requested;
        GameRunMode mode;
        boolean failBingo;

        private RecordingManager() {
            super(null);
        }

        private boolean record(String map, List<ChampionshipTeam> teams, GameRunMode mode) {
            this.map = map;
            this.requested = List.copyOf(teams);
            this.mode = mode;
            return true;
        }

        @Override
        public boolean joinMultiTeamInstanceForTeams(
                GameTypeEnum g,
                BaseMultiTeamGameInstance area,
                boolean intro,
                GameRunMode mode,
                List<ChampionshipTeam> teams) {
            return record(area.getGameConfig().getConfigName(), teams, mode);
        }

        @Override
        public List<BasePairedGameInstance> joinPairedInstances(
                GameTypeEnum g,
                String map,
                List<TwoVTwoVector> pairs,
                ArenaSelection selection,
                boolean intro,
                GameRunMode mode) {
            record(
                    map,
                    pairs.stream()
                            .flatMap(
                                    p -> java.util.stream.Stream.of(p.getTeamOne(), p.getTeamTwo()))
                            .toList(),
                    mode);
            return List.of();
        }

        @Override
        public boolean joinDodgeboltArea(
                String map,
                ChampionshipTeam right,
                ChampionshipTeam left,
                ChampionshipTeam seed,
                boolean intro,
                boolean force,
                GameRunMode mode) {
            return record(map, List.of(right, left), mode);
        }

        @Override
        public boolean joinTeamArea(
                GameTypeEnum g,
                String map,
                ChampionshipTeam right,
                ChampionshipTeam left,
                boolean intro,
                GameRunMode mode) {
            return record(map, List.of(right, left), mode);
        }

        @Override
        public CompletionStage<Boolean> joinBingoForTeams(
                String map, boolean intro, GameRunMode mode, List<ChampionshipTeam> teams) {
            record(map, teams, mode);
            return failBingo
                    ? CompletableFuture.failedFuture(new IllegalStateException("worker down"))
                    : CompletableFuture.completedFuture(true);
        }

        @Override
        public boolean canStartBingoForTeams(
                String map, boolean intro, GameRunMode mode, List<ChampionshipTeam> teams) {
            return true;
        }
    }

    private static final class ReadyPrepare extends PrepareSessionManager {
        ReadyPrepare(ChampionshipsCore p) {
            super(p);
        }

        @Override
        public boolean canStart(GameTypeEnum g, String map) {
            return map.equals("Chosen");
        }
    }

    private static final class Maps extends BaseGameInstanceManager<BaseGameInstance> {
        Maps(ChampionshipsCore p) {
            super(p);
        }

        void register(BaseGameInstance area) {
            registerMapInstances("Chosen", List.of(area));
        }

        @Override
        public void load() {}

        @Override
        public boolean addArea(String name) {
            return false;
        }
    }

    private static final class TestTeam extends ChampionshipTeam {
        TestTeam(int id, GameTypeEnum game) {
            super(
                    id,
                    "team-" + id,
                    "red",
                    "#FFFFFF",
                    game == GameTypeEnum.IAmNotARobot
                            ? Set.of(
                                    new UUID(id, 1),
                                    new UUID(id, 2),
                                    new UUID(id, 3),
                                    new UUID(id, 4))
                            : Set.of(new UUID(id, 1)),
                    null);
        }
    }
}

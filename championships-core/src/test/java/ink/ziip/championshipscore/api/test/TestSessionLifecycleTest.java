package ink.ziip.championshipscore.api.test;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.manager.GameManager;
import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.start.GameStartService;
import ink.ziip.championshipscore.api.game.tntrun.runtime.TNTRunTeamArea;
import ink.ziip.championshipscore.api.player.PlayerManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.api.team.TeamManager;
import ink.ziip.championshipscore.api.test.auth.TestAuthGateway;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@org.junit.jupiter.api.extension.ExtendWith(
        ink.ziip.championshipscore.configuration.ConfigurationStateExtension.class)
class TestSessionLifecycleTest {
    @org.junit.jupiter.api.BeforeEach
    void enableGameTestsForFixture() {
        ink.ziip.championshipscore.configuration.config.CCConfig.TEST_ENABLED = true;
    }

    @Test
    void rosterOwnershipSpansPreparationAndRestoreButReleasesDevelopersWhoLeave() throws Exception {
        try (var f = new Fixture()) {
            var session = f.ready();
            f.flush();
            UUID bot = f.ids.getFirst();
            UUID unrelated = UUID.randomUUID();
            assertTrue(f.manager.ownsRosterMember(bot));
            assertFalse(f.manager.ownsRosterMember(unrelated));
            var developer = f.player(unrelated, "developer");
            f.manager.joinDeveloper(1, developer);
            assertTrue(f.manager.ownsRosterMember(unrelated));
            assertFalse(f.manager.ownsRosterMember(bot));
            f.manager.leaveDeveloper(1, developer);
            assertFalse(f.manager.ownsRosterMember(unrelated));
            var area = f.area();
            set(f.manager, "ownedInstances", List.of(area));
            var closing = f.manager.closeActiveSessionAsync();
            UUID remaining = f.ids.get(1);
            assertTrue(f.manager.ownsRosterMember(remaining));
            area.restore.complete(false);
            assertTrue(closing.toCompletableFuture().isCompletedExceptionally());
            assertTrue(f.manager.ownsRosterMember(remaining));
            area.restore = new CompletableFuture<>();
            f.manager.closeActiveSessionAsync();
            area.restore.complete(true);
            assertEquals(TestSessionState.CLOSED, session.state());
            assertFalse(f.manager.ownsRosterMember(remaining));
        }
    }

    @Test
    void unloadRetainsTestOwnershipUntilAsynchronousRestoreCompletes() throws Exception {
        try (var f = new Fixture()) {
            var session = f.ready();
            f.flush();
            var area = f.area();
            set(f.manager, "ownedInstances", List.of(area));

            f.manager.unload();

            assertTrue(f.manager.isCleaningUp());
            assertEquals(TestSessionState.STOPPING, session.state());
            assertTrue(f.manager.ownsRosterMember(f.ids.getFirst()));

            area.restore.complete(true);
            f.flush();

            assertFalse(f.manager.isCleaningUp());
            assertEquals(TestSessionState.CLOSED, session.state());
            assertFalse(f.manager.ownsRosterMember(f.ids.getFirst()));
        }
    }

    @Test
    void developerCannotReplaceBotWhileQueuedForDaily() throws Exception {
        try (var f = new Fixture()) {
            var session = f.ready();
            f.flush();
            var developer = f.player(UUID.randomUUID(), "queuedDev");
            var daily = allocate(ink.ziip.championshipscore.api.daily.DailyManager.class);
            set(
                    daily,
                    "queueByPlayer",
                    Map.of(
                            developer.getUniqueId(),
                            ink.ziip.championshipscore.api.game.model.GameTypeEnum.TNTRun));
            set(f.plugin, "dailyManager", daily);
            assertThrows(IllegalStateException.class, () -> f.manager.joinDeveloper(1, developer));
            assertEquals(TestSessionState.READY, session.state());
            assertFalse(f.manager.ownsRosterMember(developer.getUniqueId()));
            assertTrue(f.manager.ownsRosterMember(f.ids.getFirst()));
            assertEquals(16, f.teams.live.size());
        }
    }

    @Test
    void nextRoundReadmissionTimeoutFailsClosedAndOldRetryCannotTouchNewSession() throws Exception {
        try (var f = new Fixture()) {
            var session = f.ready();
            f.flush();
            session.start();
            var orchestrator =
                    new TestEventOrchestrator(
                            f.manager,
                            new TestEventPlan(
                                    "next", List.of(new TestEventPlan.Round("tntrun", "astra"))));
            set(f.manager, "eventOrchestrator", orchestrator);
            var attempt =
                    TestSessionManager.class.getDeclaredMethod(
                            "awaitNextRoundPlayers",
                            TestEventOrchestrator.class,
                            int.class,
                            long.class);
            attempt.setAccessible(true);
            attempt.invoke(f.manager, orchestrator, 0, System.nanoTime() - 1);
            assertEquals(TestSessionState.FAILED, session.state());
            assertTrue(f.teams.live.isEmpty());
            var next = f.manager.createSession(TestSessionMode.SINGLE_GAME, roster());
            attempt.invoke(f.manager, orchestrator, 0, System.nanoTime() - 1);
            assertSame(next, f.manager.activeSession());
            assertEquals(TestSessionState.AUTHENTICATING, next.state());
        }
    }

    @Test
    void nextRoundNeedsAllConnectionsAndFreshAuthAfterPrelogin() throws Exception {
        try (var f = new Fixture()) {
            var session = f.ready();
            f.flush();
            session.start();
            assertFalse(f.manager.isRosterReadyForNextRound());
            for (int i = 0; i < 64; i++) f.player(f.ids.get(i), "testbot" + i);
            assertTrue(f.manager.isRosterReadyForNextRound());
            UUID id = f.ids.getFirst();
            var now = Instant.now();
            assertTrue(f.manager.hasUsableReconnectLease("testbot0", id));
            assertTrue(f.manager.observePreLogin("testbot0", id, now).accepted());
            assertFalse(f.manager.isRosterReadyForNextRound());
            f.manager.confirmAuthMeLogin("testbot0", id, now);
            assertTrue(f.manager.isRosterReadyForNextRound());
            session.complete();
            assertFalse(f.manager.hasUsableReconnectLease("testbot0", id));
        }
    }

    @Test
    void authenticatedSnapshotCannotStartWhileItsConnectionsAreOffline() throws Exception {
        try (var f = new Fixture()) {
            var session = f.ready();
            f.flush();
            var error = assertThrows(IllegalStateException.class, f.manager::startPlannedGame);
            assertTrue(error.getMessage().contains("offline"));
            assertEquals(TestSessionState.READY, session.state());
            assertFalse(session.roster().isFrozen());
        }
    }

    @Test
    void preparationDeveloperDisconnectRestoresVacancyAndRequiresFreshBotAdmission()
            throws Exception {
        try (var f = new Fixture()) {
            f.ready();
            f.flush();
            var developer = f.player(UUID.randomUUID(), "developer");
            f.manager.joinDeveloper(1, developer);
            f.flush();
            f.manager.onPlayerDisconnect(developer);
            f.flush();
            assertEquals(TestSessionState.AUTHENTICATING, f.manager.activeSession().state());
            assertTrue(f.teams.live.isEmpty());
            assertTrue(
                    f.manager.activeSession().roster().snapshot().stream()
                            .noneMatch(s -> s.occupantType() == TestRoster.OccupantType.DEVELOPER));
            assertFalse(
                    f.manager
                            .observePreLogin("testbot0", f.ids.getFirst(), Instant.now())
                            .accepted());
            f.authenticate(0);
            f.flush();
            assertEquals(16, f.teams.live.size());
            assertTrue(f.manager.isReady());
        }
    }

    @Test
    void preparationBotDisconnectInvalidatesReadinessAndReconnectMustAuthenticateAgain()
            throws Exception {
        try (var f = new Fixture()) {
            f.ready();
            f.flush();
            UUID id = f.ids.getFirst();
            var bot = f.player(id, "testbot0");
            f.manager.onPlayerDisconnect(bot);
            f.flush();
            assertEquals(TestSessionState.AUTHENTICATING, f.manager.activeSession().state());
            assertFalse(f.manager.activeSession().roster().isComplete());
            assertTrue(f.teams.live.isEmpty());
            assertFalse(f.cached(id));
            assertFalse(f.manager.isAuthenticatedTestIdentity("testbot0", id));
            var now = Instant.now();
            assertTrue(f.manager.observePreLogin("testbot0", id, now).accepted());
            assertFalse(f.manager.isReady());
            f.manager.confirmAuthMeLogin("testbot0", id, now);
            f.players.prepareEphemeralTestIdentity("testbot0", id);
            f.flush();
            assertTrue(f.manager.isReady());
            assertEquals(16, f.teams.live.size());
        }
    }

    @Test
    void oldConnectionQuitAndRunningQuitCannotReleaseCurrentPreparationOrFrozenRoster()
            throws Exception {
        try (var f = new Fixture()) {
            var session = f.ready();
            f.flush();
            UUID id = f.ids.getFirst();
            var old = f.player(id, "testbot0");
            var current = f.player(id, "testbot0");
            f.manager.onPlayerDisconnect(old);
            assertTrue(f.manager.isReady());
            assertTrue(f.cached(id));
            session.start();
            f.manager.onPlayerDisconnect(current);
            assertEquals(TestSessionState.RUNNING, session.state());
            assertTrue(session.roster().isComplete());
            assertTrue(f.cached(id));
            assertEquals(16, f.teams.live.size());
        }
    }

    @Test
    void successfulStartWithoutRuntimeOwnershipFailsAndReleasesPreparation() throws Exception {
        try (var f = new Fixture()) {
            var session = f.ready();
            f.flush();
            session.start();
            var plan =
                    new TestEventPlan(
                            "single", List.of(new TestEventPlan.Round("tntrun", "astra")));
            var orchestrator = new TestEventOrchestrator(f.manager, plan);
            set(f.manager, "eventOrchestrator", orchestrator);
            f.manager.finishStart(
                    session,
                    orchestrator,
                    0,
                    ink.ziip.championshipscore.api.game.start.GameFlow.STANDALONE,
                    new GameStartService.Result(true, "missing instance"),
                    null);
            assertEquals(TestSessionState.FAILED, session.state());
            assertTrue(f.teams.live.isEmpty());
            assertFalse(f.manager.isCleaningUp());
            assertFalse(f.cached(f.ids.getFirst()));
        }
    }

    @Test
    void explicitStopRetriesAFailedAutomaticCleanupImmediately() throws Exception {
        try (var f = new Fixture()) {
            var session = f.ready();
            f.flush();
            session.start();
            var area = f.area();
            set(f.manager, "ownedInstances", List.of(area));
            var context =
                    new ink.ziip.championshipscore.api.game.start.GameExecutionContext(
                            ink.ziip.championshipscore.api.game.model.GameRunMode.TEST,
                            ink.ziip.championshipscore.api.game.start.GameFlow.STANDALONE,
                            ink.ziip.championshipscore.api.game.start.SettlementPolicy.TEST,
                            session.sessionId(),
                            session.generation());
            f.manager.failOwnedSession(context);
            area.restore.complete(false);
            assertTrue(f.manager.isCleaningUp());
            area.restore = new CompletableFuture<>();
            var retry = f.manager.closeActiveSessionAsync().toCompletableFuture();
            assertFalse(retry.isDone());
            area.restore.complete(true);
            assertDoesNotThrow(retry::join);
            assertEquals(TestSessionState.CLOSED, session.state());
        }
    }

    @Test
    void developerCannotReplaceAnExistingGameplayOrSpectatorOwnership() throws Exception {
        try (var f = new Fixture()) {
            f.ready();
            f.flush();
            var developer = f.player(UUID.randomUUID(), "developer");
            var area = f.area();
            var playing =
                    (Map<UUID, BaseGameInstance>)
                            field(GameManager.class, "playerStatus").get(f.games);
            var spectating =
                    (Map<UUID, BaseGameInstance>)
                            field(GameManager.class, "playerSpectatorStatus").get(f.games);
            playing.put(developer.getUniqueId(), area);
            assertThrows(IllegalStateException.class, () -> f.manager.joinDeveloper(1, developer));
            playing.clear();
            spectating.put(developer.getUniqueId(), area);
            assertThrows(IllegalStateException.class, () -> f.manager.joinDeveloper(1, developer));
            assertFalse(f.manager.leaveDeveloperSpectator(developer));
            assertEquals(TestSessionState.READY, f.manager.activeSession().state());
            assertTrue(f.cached(f.ids.getFirst()));
            assertEquals(16, f.teams.live.size());
        }
    }

    @Test
    void stopWaitsForEveryRestoreAndNeverTouchesANewSession() throws Exception {
        try (var f = new Fixture()) {
            var session = f.ready();
            f.flush();
            var a = f.area();
            var b = f.area();
            set(f.manager, "ownedInstances", List.of(a, b));
            var close = f.manager.closeActiveSessionAsync().toCompletableFuture();
            assertNull(f.manager.activeSession());
            assertSame(session, f.manager.sessionForStatus());
            assertTrue(f.manager.isCleaningUp());
            assertEquals(16, f.teams.live.size());
            assertTrue(f.cached(f.ids.get(0)));
            assertThrows(
                    IllegalStateException.class,
                    () -> f.manager.createSession(TestSessionMode.EVENT, roster()));
            a.restore.complete(true);
            assertFalse(close.isDone());
            assertEquals(16, f.teams.live.size());
            b.restore.complete(true);
            assertTrue(close.isDone());
            assertFalse(close.isCompletedExceptionally());
            assertFalse(f.manager.isCleaningUp());
            assertTrue(f.teams.live.isEmpty());
            assertFalse(f.cached(f.ids.get(0)));
            assertEquals(TestSessionState.CLOSED, session.state());
            assertNull(f.manager.sessionForStatus());
            var next = f.manager.createSession(TestSessionMode.EVENT, roster());
            f.flush();
            assertSame(next, f.manager.activeSession());
            assertEquals(TestSessionState.AUTHENTICATING, next.state());
            assertEquals(
                    TestAuthGateway.PreLoginStatus.REJECTED,
                    f.manager.observePreLogin("testbot0", f.ids.get(0), Instant.now()).status());
            assertEquals(
                    TestAuthGateway.PreLoginStatus.NOT_TEST_IDENTITY,
                    f.manager
                            .observePreLogin("ordinary", UUID.randomUUID(), Instant.now())
                            .status());
        }
    }

    @Test
    void stopDuringPendingStartFindsInstancesWhenTheStartEventuallySettles() throws Exception {
        try (var f = new Fixture()) {
            f.ready();
            f.flush();
            var pending = new CompletableFuture<GameStartService.Result>();
            set(f.manager, "pendingStart", pending);
            var close = f.manager.closeActiveSessionAsync().toCompletableFuture();
            var area = f.area();
            f.games.owners.put(f.teams.live.getFirst(), area);
            assertFalse(close.isDone());
            assertEquals(0, area.aborts);
            pending.complete(new GameStartService.Result(true, "late start"));
            assertEquals(1, area.aborts);
            assertFalse(close.isDone());
            area.restore.complete(true);
            assertTrue(close.isDone());
            assertTrue(f.teams.live.isEmpty());
        }
    }

    @Test
    void failedRestoreKeepsAdmissionClosedAndExplicitStopCanRetry() throws Exception {
        try (var f = new Fixture()) {
            f.ready();
            f.flush();
            var area = f.area();
            set(f.manager, "ownedInstances", List.of(area));
            var close = f.manager.closeActiveSessionAsync().toCompletableFuture();
            area.restore.complete(false);
            assertTrue(close.isCompletedExceptionally());
            assertTrue(f.manager.isCleaningUp());
            assertEquals(16, f.teams.live.size());
            area.restore = new CompletableFuture<>();
            var retry = f.manager.closeActiveSessionAsync().toCompletableFuture();
            area.restore.complete(true);
            assertDoesNotThrow(retry::join);
            assertFalse(f.manager.isCleaningUp());
            assertTrue(f.teams.live.isEmpty());
        }
    }

    @Test
    void backgroundRestoreCompletionPublishesOnlyOnTheServerThread() throws Exception {
        try (var f = new Fixture()) {
            f.ready();
            f.flush();
            var area = f.area();
            set(f.manager, "ownedInstances", List.of(area));
            var close = f.manager.closeActiveSessionAsync().toCompletableFuture();
            f.main = false;
            area.restore.complete(true);
            assertFalse(close.isDone());
            assertEquals(16, f.teams.live.size());
            f.main = true;
            f.flush();
            assertDoesNotThrow(close::join);
            assertTrue(f.teams.live.isEmpty());
        }
    }

    @Test
    void developerCanMoveLeaveAndRefillReadyRosterWithoutLeakingBotIdentity() throws Exception {
        try (var f = new Fixture()) {
            f.ready();
            f.flush();
            Player developer = f.player(UUID.randomUUID(), "developer");
            UUID replaced = f.ids.get(0);
            f.manager.joinDeveloper(1, developer);
            assertFalse(f.cached(replaced));
            assertEquals(
                    TestAuthGateway.PreLoginStatus.REJECTED,
                    f.manager.observePreLogin("testbot0", replaced, Instant.now()).status());
            f.flush();
            assertEquals(16, f.teams.live.size());
            f.manager.joinDeveloper(2, developer);
            assertEquals(TestSessionState.AUTHENTICATING, f.manager.activeSession().state());
            assertTrue(f.teams.live.isEmpty());
            f.authenticate(0);
            f.flush();
            assertEquals(TestSessionState.READY, f.manager.activeSession().state());
            assertEquals("testbot4", f.manager.leaveDeveloper(2, developer));
            assertEquals(TestSessionState.AUTHENTICATING, f.manager.activeSession().state());
            assertTrue(f.teams.live.isEmpty());
            f.authenticate(4);
            f.flush();
            assertEquals(TestSessionState.READY, f.manager.activeSession().state());
            assertEquals(16, f.teams.live.size());
            assertTrue(f.teams.live.stream().allMatch(team -> team.getMembers().size() == 4));
            f.manager.closeActiveSessionAsync().toCompletableFuture().join();
            assertFalse(f.cached(replaced));
        }
    }

    @Test
    void staleMaterializationCannotReviveAReleasedDeveloperSeat() throws Exception {
        try (var f = new Fixture()) {
            f.ready();
            Player developer = f.player(UUID.randomUUID(), "developer");
            f.manager.joinDeveloper(1, developer);
            f.manager.leaveDeveloper(1, developer);
            f.flush();
            assertEquals(TestSessionState.AUTHENTICATING, f.manager.activeSession().state());
            assertTrue(f.teams.live.isEmpty());
            f.authenticate(0);
            f.flush();
            assertEquals(16, f.teams.live.size());
        }
    }

    private static TestRoster roster() {
        return TestRoster.fromBotUsernames(
                java.util.stream.IntStream.range(0, 64).mapToObj(i -> "testbot" + i).toList());
    }

    static final class Fixture implements AutoCloseable {
        final Object oldServer = field(Bukkit.class, "server").get(null);
        final ChampionshipsCore plugin = allocate(ChampionshipsCore.class);
        final FakeTeams teams = allocate(FakeTeams.class);
        final FakeGames games = allocate(FakeGames.class);
        final PlayerManager players = new PlayerManager(null);
        final TestSessionManager manager =
                new TestSessionManager(
                        plugin,
                        ink.ziip.championshipscore.api.test.auth.TestIdentityReservations
                                .inMemory(),
                        (name, uuid) -> false);
        final List<UUID> ids = new ArrayList<>();
        final Map<UUID, Player> online = new HashMap<>();
        final Map<UUID, org.bukkit.World> worlds = new HashMap<>();
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        boolean main = true;

        Fixture() throws Exception {
            teams.live = new ArrayList<>();
            games.owners = new HashMap<>();
            set(games, "playerStatus", new HashMap<UUID, BaseGameInstance>());
            set(games, "playerSpectatorStatus", new HashMap<UUID, BaseGameInstance>());
            var scheduler =
                    proxy(
                            BukkitScheduler.class,
                            (p, m, a) -> {
                                if (m.getName().equals("runTask")
                                        || m.getName().equals("runTaskLater")) {
                                    queue.add((Runnable) a[1]);
                                    return proxy(BukkitTask.class, (p2, m2, a2) -> null);
                                }
                                throw new AssertionError(m.getName());
                            });
            var server =
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "isPrimaryThread" -> main;
                                        case "getPlayer" -> online.get(a[0]);
                                        case "getWorld" -> worlds.get(a[0]);
                                        case "getScheduler" -> scheduler;
                                        default -> throw new AssertionError(m.getName());
                                    });
            field(Bukkit.class, "server").set(null, server);
            set(plugin, "server", server);
            set(plugin, "logger", java.util.logging.Logger.getAnonymousLogger());
            set(plugin, "isEnabled", true);
            set(plugin, "teamManager", teams);
            set(plugin, "playerManager", players);
            set(plugin, "gameManager", games);
            manager.load();
        }

        TestSession ready() {
            var s =
                    manager.createSession(
                            TestSessionMode.SINGLE_GAME,
                            roster(),
                            new TestSessionPlan("tntrun", "astra", "idle"));
            for (int i = 0; i < 64; i++) authenticate(i);
            return s;
        }

        void authenticate(int i) {
            UUID id = UUID.randomUUID();
            if (ids.size() > i) ids.set(i, id);
            else ids.add(id);
            Instant now = Instant.now();
            String name = "testbot" + i;
            manager.registerLease(name, id, now.plusSeconds(3600), now);
            assertTrue(manager.observePreLogin(name, id, now).accepted());
            manager.confirmAuthMeLogin(name, id, now);
            players.prepareEphemeralTestIdentity(name, id);
        }

        Player player(UUID id, String name) {
            var worldId = UUID.randomUUID();
            var world =
                    proxy(
                            org.bukkit.World.class,
                            (p, m, a) -> {
                                if (m.getName().equals("getUID")) return worldId;
                                throw new AssertionError(m.getName());
                            });
            worlds.put(worldId, world);
            var inventory =
                    proxy(
                            org.bukkit.inventory.PlayerInventory.class,
                            (p, m, a) -> {
                                if (m.getName().equals("getStorageContents"))
                                    return new org.bukkit.inventory.ItemStack[36];
                                if (m.getName().equals("getArmorContents"))
                                    return new org.bukkit.inventory.ItemStack[4];
                                if (m.getName().equals("getItemInOffHand")) return null;
                                if (m.getName().startsWith("set")) return null;
                                throw new AssertionError(m.getName());
                            });
            var enderChest =
                    proxy(
                            org.bukkit.inventory.Inventory.class,
                            (p, m, a) -> {
                                if (m.getName().equals("getContents"))
                                    return new org.bukkit.inventory.ItemStack[27];
                                if (m.getName().equals("setContents")) return null;
                                throw new AssertionError(m.getName());
                            });
            var player =
                    proxy(
                            Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getName" -> name;
                                        case "isOnline" -> true;
                                        case "kick" -> null;
                                        case "teleport" -> true;
                                        case "setGameMode",
                                                "setLevel",
                                                "setTotalExperience",
                                                "setExp",
                                                "setFoodLevel",
                                                "setSaturation",
                                                "setExhaustion",
                                                "setFireTicks",
                                                "setFallDistance",
                                                "setFlySpeed",
                                                "setWalkSpeed",
                                                "setAllowFlight",
                                                "setFlying",
                                                "setHealth",
                                                "setItemOnCursor" ->
                                                null;
                                        case "removePotionEffect", "addPotionEffect" -> true;
                                        case "getLocation" ->
                                                new org.bukkit.Location(world, 1, 65, 2, 30, 10);
                                        case "getInventory" -> inventory;
                                        case "getEnderChest" -> enderChest;
                                        case "getItemOnCursor" -> null;
                                        case "getActivePotionEffects" -> java.util.Set.of();
                                        case "getGameMode" -> org.bukkit.GameMode.SURVIVAL;
                                        case "getLevel",
                                                "getTotalExperience",
                                                "getFoodLevel",
                                                "getFireTicks" ->
                                                0;
                                        case "getExp",
                                                "getSaturation",
                                                "getExhaustion",
                                                "getFallDistance",
                                                "getFlySpeed",
                                                "getWalkSpeed" ->
                                                0F;
                                        case "getHealth", "getMaxHealth" -> 20D;
                                        case "getAllowFlight", "isFlying" -> false;
                                        default -> throw new AssertionError(m.getName());
                                    });
            online.put(id, player);
            return player;
        }

        FakeArea area() throws Exception {
            var area = allocate(FakeArea.class);
            area.restore = new CompletableFuture<>();
            set(area, "gameStageEnum", GameStageEnum.PROGRESS);
            return area;
        }

        boolean cached(UUID id) throws Exception {
            return ((Map<?, ?>) field(PlayerManager.class, "cachedPlayers").get(players))
                    .containsKey(id);
        }

        void flush() {
            while (!queue.isEmpty()) queue.removeFirst().run();
        }

        public void close() throws Exception {
            field(Bukkit.class, "server").set(null, oldServer);
        }
    }

    static class FakeTeams extends TeamManager {
        List<ChampionshipTeam> live;

        FakeTeams() {
            super(null);
        }

        @Override
        public synchronized ChampionshipTeam createTransientTeam(
                String id, String name, String color, String code, Set<UUID> ids) {
            ChampionshipTeam team = new FakeTeam(name, ids);
            live.add(team);
            return team;
        }

        @Override
        public synchronized void removeTransientTeams(Collection<ChampionshipTeam> teams) {
            live.removeAll(teams);
        }

        @Override
        public boolean isTransientTeam(ChampionshipTeam team) {
            return live.contains(team);
        }
    }

    static class FakeTeam extends ChampionshipTeam {
        FakeTeam(String name, Set<UUID> members) {
            super(-1, name, "RED", "#ff0000", members, null);
        }
    }

    static class FakeGames extends GameManager {
        Map<ChampionshipTeam, BaseGameInstance> owners;

        FakeGames() {
            super(null);
        }

        @Override
        public BaseGameInstance getTeamCurrenArea(ChampionshipTeam team) {
            return owners.get(team);
        }
    }

    static class FakeArea extends TNTRunTeamArea {
        CompletableFuture<Boolean> restore;
        int aborts;

        FakeArea() {
            super(null, null, false, null);
        }

        @Override
        public CompletableFuture<Boolean> abortAndReset() {
            aborts++;
            return restore;
        }
    }
}

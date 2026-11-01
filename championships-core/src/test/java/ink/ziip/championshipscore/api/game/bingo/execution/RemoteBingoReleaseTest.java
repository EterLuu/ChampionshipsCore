package ink.ziip.championshipscore.api.game.bingo.execution;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.*;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;

@org.junit.jupiter.api.extension.ExtendWith(
        ink.ziip.championshipscore.configuration.ConfigurationStateExtension.class)
class RemoteBingoReleaseTest {
    @org.junit.jupiter.api.BeforeEach
    void enableGameTestsForFixture() {
        ink.ziip.championshipscore.configuration.config.CCConfig.TEST_ENABLED = true;
    }

    @Test
    void abortDisconnectMaskContainsOnlyCoreAuthenticatedEphemeralIdentities() throws Exception {
        var match = match(MatchRunMode.TEST, true);
        var bot = UUID.randomUUID();
        var developer = UUID.randomUUID();
        var old = match.manifest();
        set(
                match,
                "manifest",
                new MatchManifest(
                        old.protocolVersion(),
                        old.matchId(),
                        old.epoch(),
                        old.createdAtEpochMilli(),
                        old.workerId(),
                        old.runMode(),
                        old.durationSeconds(),
                        old.cardSeed(),
                        old.configHash(),
                        old.scoring(),
                        old.runtimeRules(),
                        old.tasks(),
                        old.teams(),
                        List.of(
                                new PlayerSnapshot(bot, "testbot", ParticipantRole.SPECTATOR, null),
                                new PlayerSnapshot(
                                        developer, "developer", ParticipantRole.SPECTATOR, null))));
        var plugin = allocate(ink.ziip.championshipscore.ChampionshipsCore.class);
        var players = new ink.ziip.championshipscore.api.player.PlayerManager(null);
        players.prepareEphemeralTestIdentity("testbot", bot);
        players.addPlayer(developer);
        set(plugin, "playerManager", players);
        set(match, "plugin", plugin);
        assertEquals(
                bot.toString(), match.abortCommand("test").attributes().get("disconnectPlayers"));
        var sessions = new ink.ziip.championshipscore.api.test.TestSessionManager(null);
        sessions.createSession(
                ink.ziip.championshipscore.api.test.TestSessionMode.SINGLE_GAME,
                ink.ziip.championshipscore.api.test.TestRoster.fromBotUsernames(
                        java.util.stream.IntStream.range(0, 64)
                                .mapToObj(i -> i == 0 ? "testbot" : "testbot" + i)
                                .toList()));
        var now = java.time.Instant.now();
        sessions.registerLease("testbot", bot, now.plusSeconds(3600), now);
        set(plugin, "testSessionManager", sessions);
        assertEquals(
                "",
                match.abortCommand("natural-first-round").attributes().get("disconnectPlayers"));
        sessions.closeActiveSession();
        assertEquals(
                bot.toString(),
                match.abortCommand("explicit-stop").attributes().get("disconnectPlayers"));
        assertFalse(
                match(MatchRunMode.GAME, true)
                        .abortCommand("normal")
                        .attributes()
                        .containsKey("disconnectPlayers"));
    }

    private RemoteBingoMatch match(MatchRunMode mode, boolean terminal) throws Exception {
        var manifest =
                new MatchManifest(
                        ProtocolVersion.CURRENT,
                        UUID.randomUUID(),
                        1,
                        1,
                        "worker",
                        mode,
                        60,
                        1,
                        "test",
                        new BingoScoringRules(1, List.of(1), 0, 0, 0),
                        new BingoRuntimeRules(
                                90,
                                100,
                                10,
                                130,
                                List.of(),
                                false,
                                45,
                                List.of(),
                                new BingoPresentation(Map.of())),
                        List.of(new BingoTaskSpec(0, "stone", "item", Map.of("material", "STONE"))),
                        List.of(),
                        List.of());
        var match = allocate(RemoteBingoMatch.class);
        set(match, "manifest", manifest);
        var state = new MatchStateMachine();
        if (terminal) state.transitionTo(MatchState.ABORTED);
        set(match, "lifecycle", state);
        set(match, "resourcesReleased", new CompletableFuture<Void>());
        set(match, "terminal", new CompletableFuture<Void>());
        set(match, "spectatorAddAcks", new HashMap<>());
        set(match, "spectatorRemoveAcks", new HashMap<>());
        return match;
    }

    private MatchEvent event(RemoteBingoMatch match, long seq, MatchEventType type, String worlds) {
        return MatchMessages.event(
                match.manifest().matchId(),
                1,
                seq,
                type,
                Map.of("worlds", worlds),
                Clock.systemUTC());
    }

    @Test
    void unclaimedReleaseRequiresDisabledAdmissionBeforeAnyWorkerReadyEvenAfterLocalAbort()
            throws Exception {
        for (boolean locallyAborted : List.of(false, true)) {
            var match = match(MatchRunMode.TEST_EVENT, locallyAborted);
            assertFalse(
                    match.apply(event(match, 1, MatchEventType.RELEASED, "unclaimed"))
                            .toCompletableFuture()
                            .join());
            var failed =
                    MatchMessages.event(
                            match.manifest().matchId(),
                            1,
                            1,
                            MatchEventType.PREPARE_FAILED,
                            Map.of("reason", "test-disabled"),
                            Clock.systemUTC());
            assertTrue(match.apply(failed).toCompletableFuture().join());
            assertTrue(
                    match.apply(event(match, 2, MatchEventType.RELEASED, "unclaimed"))
                            .toCompletableFuture()
                            .join());
            match.confirmResourcesReleased();
            assertTrue(match.resourcesReleased());
        }
        var acquired = match(MatchRunMode.TEST, false);
        // Even a READY rejected before routing is evidence that the backend may own resources.
        assertFalse(
                acquired.apply(
                                MatchMessages.event(
                                        acquired.manifest().matchId(),
                                        1,
                                        1,
                                        MatchEventType.READY,
                                        Map.of(),
                                        Clock.systemUTC()))
                        .toCompletableFuture()
                        .join());
        assertTrue(
                acquired.apply(
                                MatchMessages.event(
                                        acquired.manifest().matchId(),
                                        1,
                                        1,
                                        MatchEventType.PREPARE_FAILED,
                                        Map.of("reason", "test-disabled"),
                                        Clock.systemUTC()))
                        .toCompletableFuture()
                        .join());
        assertFalse(
                acquired.apply(event(acquired, 2, MatchEventType.RELEASED, "unclaimed"))
                        .toCompletableFuture()
                        .join());
        assertThrows(IllegalStateException.class, acquired::confirmResourcesReleased);
    }

    @Test
    void abortAndInFlightEventsAreNotReleaseAndSequenceGapsStillReject() throws Exception {
        var match = match(MatchRunMode.TEST_EVENT, true);
        assertFalse(
                match.apply(event(match, 2, MatchEventType.RELEASED, "reused"))
                        .toCompletableFuture()
                        .join());
        assertTrue(
                match.apply(event(match, 1, MatchEventType.PLAYER_LEFT, "ignored"))
                        .toCompletableFuture()
                        .join());
        assertFalse(match.resourcesReleasedFuture().isDone());
        assertTrue(
                match.apply(event(match, 2, MatchEventType.RELEASED, "reused"))
                        .toCompletableFuture()
                        .join());
        assertFalse(match.resourcesReleasedFuture().isDone());
        match.confirmResourcesReleased();
        assertTrue(match.resourcesReleased());
        assertTrue(
                match.apply(event(match, 2, MatchEventType.RELEASED, "reused"))
                        .toCompletableFuture()
                        .join());
    }

    @Test
    void formalNonterminalAndMalformedReleaseNeverUnlockOwnership() throws Exception {
        for (var mode : MatchRunMode.values()) {
            var match = match(mode, false);
            assertFalse(
                    match.apply(event(match, 1, MatchEventType.RELEASED, "reused"))
                            .toCompletableFuture()
                            .join());
            assertThrows(IllegalStateException.class, match::confirmResourcesReleased);
            var terminal = match(mode, true);
            assertFalse(
                    terminal.apply(event(terminal, 1, MatchEventType.RELEASED, "queued"))
                            .toCompletableFuture()
                            .join());
            if (!mode.test())
                assertFalse(
                        terminal.apply(event(terminal, 1, MatchEventType.RELEASED, "reset"))
                                .toCompletableFuture()
                                .join());
        }
    }

    @Test
    void releaseAcknowledgementCompletesTestOwnershipBeforeLocalCleanup() throws Exception {
        var match = match(MatchRunMode.TEST, true);
        var event = event(match, 1, MatchEventType.RELEASED, "reset");
        assertTrue(match.apply(event).toCompletableFuture().join());
        var ids = ConcurrentHashMap.<UUID>newKeySet();
        ids.add(event.matchId());
        match.resourcesReleasedFuture().thenRun(ids::clear);
        match.confirmResourcesReleased();
        assertTrue(ids.isEmpty());
        assertTrue(match.resourcesReleased());
    }

    @Test
    void testReleaseCanAdvanceAfterCorePluginReferenceIsGone() throws Exception {
        var match = match(MatchRunMode.TEST_EVENT, true);
        var manager = allocate(RemoteBingoManager.class);
        var event = event(match, 1, MatchEventType.RELEASED, "reset");

        // The consumer may finish a Worker delivery after Core has already torn down its plugin
        // object. RELEASED is the one event that only changes the in-memory ownership barrier.
        assertTrue(manager.applyEvent(event, match).toCompletableFuture().join());
        match.confirmResourcesReleased();
        assertTrue(match.resourcesReleasedFuture().isDone());
    }

    @Test
    void testReleaseCanAdvanceWhenCorePluginIsDisabled() throws Exception {
        var plugin = allocate(ink.ziip.championshipscore.ChampionshipsCore.class);
        plugin.setEnabled(false);
        assertFalse(plugin.isEnabled());
        var manager = new RemoteBingoManager(plugin);
        var match = match(MatchRunMode.TEST, true);
        var event = event(match, 1, MatchEventType.RELEASED, "reused");

        assertTrue(manager.applyEvent(event, match).toCompletableFuture().join());
        match.confirmResourcesReleased();
        assertTrue(match.resourcesReleased());
    }
}

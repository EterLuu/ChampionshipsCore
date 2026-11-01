package ink.ziip.championshipscore.api.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.UUID;

@org.junit.jupiter.api.extension.ExtendWith(
        ink.ziip.championshipscore.configuration.ConfigurationStateExtension.class)
class TestSessionManagerTest {
    @org.junit.jupiter.api.BeforeEach
    void enableGameTestsForFixture() {
        ink.ziip.championshipscore.configuration.config.CCConfig.TEST_ENABLED = true;
    }

    @Test
    void disabledGateBlocksAdmissionButPreservesHistoricalDenialsAndCleanup(
            @TempDir java.nio.file.Path dir) {
        var file = dir.resolve("identities.tsv");
        var manager =
                new TestSessionManager(
                        null,
                        new ink.ziip.championshipscore.api.test.auth.TestIdentityReservations(
                                file));
        var now = java.time.Instant.now();
        UUID id = UUID.randomUUID();
        var session = manager.createSession(TestSessionMode.SINGLE_GAME, roster());
        manager.registerLease("testbot0", id, now.plusSeconds(600), now);
        manager.observePreLogin("testbot0", id, now);
        manager.confirmAuthMeLogin("testbot0", id, now);

        ink.ziip.championshipscore.configuration.config.CCConfig.TEST_ENABLED = false;
        assertFalse(manager.isEnabled());
        assertFalse(manager.isAuthenticatedTestIdentity("testbot0", id));
        assertFalse(manager.hasUsableReconnectLease("testbot0", id));
        assertFalse(manager.observePreLogin("testbot0", id, now).accepted());
        assertThrows(
                IllegalStateException.class, () -> manager.confirmAuthMeLogin("testbot0", id, now));
        assertThrows(
                IllegalStateException.class,
                () ->
                        manager.registerLease(
                                "testbot1", UUID.randomUUID(), now.plusSeconds(600), now));
        assertThrows(IllegalStateException.class, manager::startPlannedGame);
        manager.closeActiveSessionAsync().toCompletableFuture().join();
        assertEquals(TestSessionState.CLOSED, session.state());
        assertNull(manager.activeSession());
        var restarted =
                new TestSessionManager(
                        null,
                        new ink.ziip.championshipscore.api.test.auth.TestIdentityReservations(
                                file));
        for (var mode : TestSessionMode.values())
            assertThrows(
                    IllegalStateException.class, () -> restarted.createSession(mode, roster()));
        assertEquals(
                ink.ziip.championshipscore.api.test.auth.TestAuthGateway.PreLoginStatus.REJECTED,
                restarted.observePreLogin("renamed", id, now).status());
        assertEquals(
                ink.ziip.championshipscore.api.test.auth.TestAuthGateway.PreLoginStatus.REJECTED,
                restarted.observePreLogin("testbot0", UUID.randomUUID(), now).status());
        assertEquals(
                ink.ziip.championshipscore.api.test.auth.TestAuthGateway.PreLoginStatus
                        .NOT_TEST_IDENTITY,
                restarted.observePreLogin("ordinary", UUID.randomUUID(), now).status());
        ink.ziip.championshipscore.configuration.config.CCConfig.TEST_ENABLED = null;
        assertThrows(
                IllegalStateException.class,
                () -> restarted.createSession(TestSessionMode.SINGLE_GAME, roster()));
    }

    @Test
    void restartedManagerRejectsOldNamesAndRenamedUuidsUntilNewTrustedLease(
            @TempDir java.nio.file.Path dir) {
        var file = dir.resolve("identities.tsv");
        var manager =
                new TestSessionManager(
                        null,
                        new ink.ziip.championshipscore.api.test.auth.TestIdentityReservations(
                                file));
        var now = java.time.Instant.now();
        UUID id = UUID.randomUUID();
        manager.createSession(TestSessionMode.SINGLE_GAME, roster());
        manager.registerLease("testbot0", id, now.plusSeconds(600), now);
        assertTrue(manager.observePreLogin("testbot0", id, now).accepted());
        manager.confirmAuthMeLogin("testbot0", id, now);
        // A new manager models process loss: no old session or lease can survive.
        var restarted =
                new TestSessionManager(
                        null,
                        new ink.ziip.championshipscore.api.test.auth.TestIdentityReservations(
                                file));
        assertEquals(
                ink.ziip.championshipscore.api.test.auth.TestAuthGateway.PreLoginStatus.REJECTED,
                restarted.observePreLogin("testbot0", UUID.randomUUID(), now).status());
        assertEquals(
                ink.ziip.championshipscore.api.test.auth.TestAuthGateway.PreLoginStatus.REJECTED,
                restarted.observePreLogin("renamed", id, now).status());
        assertEquals(
                ink.ziip.championshipscore.api.test.auth.TestAuthGateway.PreLoginStatus
                        .NOT_TEST_IDENTITY,
                restarted.observePreLogin("ordinary", UUID.randomUUID(), now).status());
        restarted.createSession(TestSessionMode.SINGLE_GAME, roster());
        restarted.registerLease("testbot0", id, now.plusSeconds(600), now);
        assertFalse(restarted.observePreLogin("testbot0", UUID.randomUUID(), now).accepted());
        assertTrue(restarted.observePreLogin("testbot0", id, now).accepted());
        assertFalse(restarted.isAuthenticatedTestIdentity("testbot0", id));
        restarted.confirmAuthMeLogin("testbot0", id, now);
        assertTrue(restarted.isAuthenticatedTestIdentity("testbot0", id));
    }

    @Test
    void failedReservationWriteRevokesTheUnpublishedLease(@TempDir java.nio.file.Path dir)
            throws Exception {
        var parent = dir.resolve("blocked");
        java.nio.file.Files.writeString(parent, "file");
        var manager =
                new TestSessionManager(
                        null,
                        new ink.ziip.championshipscore.api.test.auth.TestIdentityReservations(
                                parent.resolve("ids.tsv")));
        var session = manager.createSession(TestSessionMode.SINGLE_GAME, roster());
        var now = java.time.Instant.now();
        UUID id = UUID.randomUUID();
        assertThrows(
                java.io.UncheckedIOException.class,
                () -> manager.registerLease("testbot0", id, now.plusSeconds(60), now));
        assertEquals(0, session.identities().size());
        assertFalse(manager.observePreLogin("testbot0", id, now).accepted());
        java.nio.file.Files.delete(parent);
        manager.registerLease("testbot0", id, now.plusSeconds(60), now);
        assertTrue(manager.observePreLogin("testbot0", id, now).accepted());
    }

    @Test
    void createsOnlyOneAuthenticationPendingSession() {
        TestSessionManager manager = new TestSessionManager(null);
        TestSession first = manager.createSession(TestSessionMode.SINGLE_GAME, roster());

        assertEquals(TestSessionState.AUTHENTICATING, first.state());
        assertNotNull(manager.activeSession());
        assertThrows(
                IllegalStateException.class,
                () -> manager.createSession(TestSessionMode.EVENT, roster()));
    }

    @Test
    void formalIdentityConflictRejectsLeaseBeforePersistentReservation() {
        var reservations =
                ink.ziip.championshipscore.api.test.auth.TestIdentityReservations.inMemory();
        TestSessionManager manager =
                new TestSessionManager(null, reservations, (name, uuid) -> true);
        manager.createSession(TestSessionMode.SINGLE_GAME, roster());
        UUID id = UUID.randomUUID();
        var now = java.time.Instant.now();

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.registerLease("testbot0", id, now.plusSeconds(60), now));
        assertFalse(reservations.contains("testbot0", id));
        assertNull(manager.activeSession().identities().snapshot("testbot0"));
    }

    @Test
    void identityVerificationFailureFailsClosedBeforeLeaseRegistration() {
        var reservations =
                ink.ziip.championshipscore.api.test.auth.TestIdentityReservations.inMemory();
        TestSessionManager manager =
                new TestSessionManager(
                        null,
                        reservations,
                        (name, uuid) -> {
                            throw new IllegalStateException("identity store unavailable");
                        });
        manager.createSession(TestSessionMode.SINGLE_GAME, roster());
        UUID id = UUID.randomUUID();
        var now = java.time.Instant.now();

        assertThrows(
                IllegalStateException.class,
                () -> manager.registerLease("testbot0", id, now.plusSeconds(60), now));
        assertFalse(reservations.contains("testbot0", id));
        assertNull(manager.activeSession().identities().snapshot("testbot0"));
    }

    @Test
    void closeRevokesLeasesAndReleasesTheSession() {
        TestSessionManager manager = new TestSessionManager(null);
        TestSession session = manager.createSession(TestSessionMode.EVENT, roster());
        UUID expected = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();
        session.identities()
                .register(
                        new TestIdentityRegistry.Lease(
                                session.sessionId(),
                                session.generation(),
                                "testbot0",
                                expected,
                                now.plusSeconds(60)),
                        now);

        manager.closeActiveSession();

        assertEquals(TestSessionState.CLOSED, session.state());
        assertEquals(0, session.identities().size());
        assertNull(manager.activeSession());
    }

    @Test
    void unloadIsIdempotentAndDoesNotRestoreMemoryState() {
        TestSessionManager manager = new TestSessionManager(null);
        TestSession session = manager.createSession(TestSessionMode.SINGLE_GAME, roster());
        java.time.Instant now = java.time.Instant.now();
        session.identities()
                .register(
                        new TestIdentityRegistry.Lease(
                                session.sessionId(),
                                session.generation(),
                                "testbot0",
                                UUID.randomUUID(),
                                now.plusSeconds(60)),
                        now);

        manager.unload();
        manager.unload();
        manager.load();

        assertEquals(TestSessionState.CLOSED, session.state());
        assertEquals(0, session.identities().size());
        assertNull(manager.activeSession());
    }

    @Test
    void authBoundaryMarksOnlyAuthenticatedTestPlayersAsEphemeral() {
        TestSessionManager manager = new TestSessionManager(null);
        UUID expected = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();
        TestSession session = manager.createSession(TestSessionMode.SINGLE_GAME, roster());
        session.identities()
                .register(
                        new TestIdentityRegistry.Lease(
                                session.sessionId(),
                                session.generation(),
                                "testbot0",
                                expected,
                                now.plusSeconds(60)),
                        now);

        assertFalse(manager.isReservedTestIdentity("ordinary", expected));
        assertTrue(manager.observePreLogin("testbot0", expected, now).accepted());
        assertTrue(manager.isReservedTestIdentity("testbot0", expected));
        assertFalse(manager.isAuthenticatedTestIdentity("testbot0", expected));
        manager.confirmAuthMeLogin("testbot0", expected, now);
        assertTrue(manager.isAuthenticatedTestIdentity("testbot0", expected));
    }

    @Test
    void authenticatingAllBoundBotsTransitionsToReadyWithoutFormalTeams() {
        TestSessionManager manager = new TestSessionManager(null);
        TestSession session = manager.createSession(TestSessionMode.SINGLE_GAME, roster());
        java.time.Instant now = java.time.Instant.now();
        for (int index = 0; index < TestRoster.TOTAL_SEATS; index++) {
            String name = "testbot" + index;
            UUID expected = UUID.randomUUID();
            manager.registerLease(name, expected, now.plusSeconds(60), now);
            assertTrue(manager.observePreLogin(name, expected, now).accepted());
            manager.confirmAuthMeLogin(name, expected, now);
        }

        assertEquals(TestSessionState.READY, session.state());
        assertTrue(session.roster().isComplete());
        assertTrue(manager.transientTeams().isEmpty());
    }

    @Test
    void signedLeaseIsConsumedOnceAndBindsTheActiveSession() {
        ink.ziip.championshipscore.configuration.config.CCConfig.TEST_AUTH_SERVER_ID = "core-test";
        ink.ziip.championshipscore.configuration.config.CCConfig.TEST_AUTH_HMAC_SECRET =
                "isolated-test-signing-key-32-bytes-minimum";
        TestSessionManager manager = new TestSessionManager(null);
        TestSession session = manager.createSession(TestSessionMode.SINGLE_GAME, roster());
        java.time.Instant now = java.time.Instant.ofEpochSecond(1000);
        UUID profile = UUID.randomUUID();
        var claims =
                new ink.ziip.championshipscore.auth.TestLeaseToken.Claims(
                        "core-test",
                        session.sessionId(),
                        session.generation(),
                        "testbot0",
                        profile,
                        1600,
                        "abcdefghijklmnop");
        String token =
                ink.ziip.championshipscore.auth.TestLeaseToken.sign(
                        claims,
                        ink.ziip.championshipscore.configuration.config.CCConfig
                                .TEST_AUTH_HMAC_SECRET);

        manager.registerSignedLease(token, now);
        assertEquals(profile, session.identities().snapshot("testbot0").expectedProfileUuid());
        assertThrows(IllegalStateException.class, () -> manager.registerSignedLease(token, now));
    }

    private static TestRoster roster() {
        return TestRoster.fromBotUsernames(
                java.util.stream.IntStream.range(0, TestRoster.TOTAL_SEATS)
                        .mapToObj(index -> "testbot" + index)
                        .toList());
    }
}

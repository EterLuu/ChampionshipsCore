package ink.ziip.championshipscore.api.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.api.test.auth.TestAuthGateway;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

class TestAuthGatewayTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void preLoginUsesServerUuidAndAuthMeIsASeparateConfirmation() {
        UUID expected = UUID.randomUUID();
        TestSession session = session(expected);
        TestAuthGateway gateway = new TestAuthGateway(session);

        TestAuthGateway.PreLoginResult result = gateway.observePreLogin("botone", expected, NOW);
        assertTrue(result.accepted());
        assertEquals(TestIdentityRegistry.BindingState.OBSERVED, result.binding().state());
        assertEquals(
                TestIdentityRegistry.BindingState.AUTHENTICATED,
                gateway.markAuthMeLogin("botone", expected, NOW).state());
    }

    @Test
    void unknownNameRemainsNormalPlayerPathAndMismatchedLeasedNameIsRejected() {
        UUID expected = UUID.randomUUID();
        TestSession session = session(expected);
        TestAuthGateway gateway = new TestAuthGateway(session);

        assertEquals(
                TestAuthGateway.PreLoginStatus.NOT_TEST_IDENTITY,
                gateway.observePreLogin("ordinary", UUID.randomUUID(), NOW).status());
        TestAuthGateway.PreLoginResult rejected =
                gateway.observePreLogin("botone", UUID.randomUUID(), NOW);
        assertFalse(rejected.accepted());
        assertEquals(TestAuthGateway.PreLoginStatus.REJECTED, rejected.status());
        assertThrows(
                IllegalStateException.class,
                () -> gateway.markAuthMeLogin("botone", expected, NOW));
    }

    @Test
    void terminalSessionCannotAcceptPreLogin() {
        UUID expected = UUID.randomUUID();
        TestSession session = session(expected);
        TestAuthGateway gateway = new TestAuthGateway(session);
        session.fail();

        TestAuthGateway.PreLoginResult result = gateway.observePreLogin("botone", expected, NOW);
        assertEquals(TestAuthGateway.PreLoginStatus.REJECTED, result.status());
    }

    @Test
    void terminalTestSessionNeverRejectsAnUnleasedOrdinaryPlayer() {
        for (TestSessionState terminal :
                java.util.List.of(
                        TestSessionState.FAILED,
                        TestSessionState.CLOSED,
                        TestSessionState.COMPLETED)) {
            UUID botUuid = UUID.randomUUID();
            TestSession session = session(botUuid);
            TestAuthGateway gateway = new TestAuthGateway(session);
            if (terminal == TestSessionState.COMPLETED) {
                for (int index = 0; index < TestRoster.TOTAL_SEATS; index++)
                    session.roster().bindBot("testbot" + index, UUID.randomUUID());
                session.markReady();
                session.start();
                session.complete();
            } else {
                session.fail();
                if (terminal == TestSessionState.CLOSED) session.close();
            }
            assertEquals(
                    TestAuthGateway.PreLoginStatus.NOT_TEST_IDENTITY,
                    gateway.observePreLogin("ordinary", UUID.randomUUID(), NOW).status(),
                    terminal.name());
            assertEquals(
                    TestAuthGateway.PreLoginStatus.REJECTED,
                    gateway.observePreLogin("botone", botUuid, NOW).status(),
                    terminal.name());
        }
    }

    @Test
    void ordinaryNamesAreNotSubjectToTestBotNamingValidation() {
        TestSession session = session(UUID.randomUUID());
        TestAuthGateway gateway = new TestAuthGateway(session);
        for (String name : java.util.List.of("a", "ab", ".bedrock")) {
            UUID uuid = UUID.randomUUID();
            assertEquals(
                    TestAuthGateway.PreLoginStatus.NOT_TEST_IDENTITY,
                    gateway.observePreLogin(name, uuid, NOW).status());
            assertFalse(session.identities().isReserved(name, uuid));
            assertFalse(session.identities().isAuthenticated(name, uuid));
        }
    }

    @Test
    void reconnectCanReuseOnlyTheSameServerUuidAndNeedsAuthMeAgain() {
        UUID expected = UUID.randomUUID();
        TestSession session = session(expected);
        TestAuthGateway gateway = new TestAuthGateway(session);

        gateway.observePreLogin("botone", expected, NOW);
        gateway.markAuthMeLogin("botone", expected, NOW);

        assertEquals(
                TestIdentityRegistry.BindingState.OBSERVED,
                gateway.observePreLogin("botone", expected, NOW).binding().state());
        assertEquals(
                TestIdentityRegistry.BindingState.AUTHENTICATED,
                gateway.markAuthMeLogin("botone", expected, NOW).state());
        assertEquals(
                TestAuthGateway.PreLoginStatus.REJECTED,
                gateway.observePreLogin("botone", UUID.randomUUID(), NOW).status());
    }

    private static TestSession session(UUID expected) {
        TestIdentityRegistry registry = new TestIdentityRegistry();
        UUID sessionId = UUID.randomUUID();
        registry.register(
                new TestIdentityRegistry.Lease(
                        sessionId, 1, "botone", expected, NOW.plusSeconds(60)),
                NOW);
        TestSession session =
                new TestSession(
                        sessionId,
                        TestSessionMode.SINGLE_GAME,
                        TestRoster.fromBotUsernames(
                                java.util.stream.IntStream.range(0, TestRoster.TOTAL_SEATS)
                                        .mapToObj(index -> "testbot" + index)
                                        .toList()),
                        registry);
        session.beginAuthentication();
        return session;
    }
}

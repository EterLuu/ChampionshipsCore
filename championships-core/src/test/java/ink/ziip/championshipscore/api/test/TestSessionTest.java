package ink.ziip.championshipscore.api.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class TestSessionTest {
    @Test
    void sessionRequiresACompleteRosterBeforeRunning() {
        TestRoster roster = TestRoster.fromBotUsernames(names());
        TestSession session =
                new TestSession(
                        UUID.randomUUID(),
                        TestSessionMode.EVENT,
                        roster,
                        new TestIdentityRegistry());

        session.beginAuthentication();
        assertThrows(IllegalStateException.class, session::markReady);
        assertEquals(TestSessionState.AUTHENTICATING, session.state());
    }

    @Test
    void stopInvalidatesDelayedCallbacksAndCloseIsTerminal() {
        TestRoster roster = TestRoster.fromBotUsernames(names());
        UUID sessionId = UUID.randomUUID();
        TestSession session =
                new TestSession(
                        sessionId, TestSessionMode.SINGLE_GAME, roster, new TestIdentityRegistry());
        for (int index = 0; index < TestRoster.TOTAL_SEATS; index++)
            roster.bindBot("testbot" + index, UUID.randomUUID());

        session.beginAuthentication();
        session.markReady();
        long runningGeneration = session.generation();
        session.start();
        assertTrue(session.isCurrent(runningGeneration));

        session.requestStop();
        assertFalse(session.isCurrent(runningGeneration));
        assertEquals(TestSessionState.STOPPING, session.state());
        session.close();
        assertEquals(TestSessionState.CLOSED, session.state());
        assertThrows(IllegalStateException.class, session::requestStop);
    }

    @Test
    void failedSessionAlsoInvalidatesItsGeneration() {
        TestRoster roster = TestRoster.fromBotUsernames(names());
        TestSession session =
                new TestSession(
                        UUID.randomUUID(),
                        TestSessionMode.SINGLE_GAME,
                        roster,
                        new TestIdentityRegistry());
        long generation = session.generation();

        session.fail();
        assertEquals(TestSessionState.FAILED, session.state());
        assertFalse(session.isCurrent(generation));
        session.close();
        assertEquals(TestSessionState.CLOSED, session.state());
    }

    @Test
    void completedEventCanBeClosedWithoutReenteringStopping() {
        TestRoster roster = TestRoster.fromBotUsernames(names());
        TestSession session =
                new TestSession(
                        UUID.randomUUID(),
                        TestSessionMode.EVENT,
                        roster,
                        new TestIdentityRegistry(),
                        new TestSessionPlan("smoke", null, "idle"),
                        new TestEventPlan(
                                "smoke",
                                java.util.List.of(new TestEventPlan.Round("tntrun", "astra"))));
        for (int index = 0; index < TestRoster.TOTAL_SEATS; index++)
            roster.bindBot("testbot" + index, UUID.randomUUID());
        session.beginAuthentication();
        session.markReady();
        session.start();
        session.complete();

        assertEquals(TestSessionState.COMPLETED, session.state());
        session.close();
        assertEquals(TestSessionState.CLOSED, session.state());
    }

    private static java.util.List<String> names() {
        return java.util.stream.IntStream.range(0, TestRoster.TOTAL_SEATS)
                .mapToObj(index -> "testbot" + index)
                .toList();
    }
}

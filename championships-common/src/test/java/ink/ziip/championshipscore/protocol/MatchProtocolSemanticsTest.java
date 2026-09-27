package ink.ziip.championshipscore.protocol;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchProtocolSemanticsTest {
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000L), ZoneOffset.UTC);

    @Test
    void commandIdentityIsIndependentOfAttributeInsertionOrder() {
        UUID matchId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        Map<String, String> first = new LinkedHashMap<>();
        first.put("worker", "bingo-1");
        first.put("hash", "abc");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("hash", "abc");
        second.put("worker", "bingo-1");

        MatchCommand a = MatchMessages.command(matchId, 4, MatchCommandType.PREPARE, first, CLOCK);
        MatchCommand b = MatchMessages.command(matchId, 4, MatchCommandType.PREPARE, second, CLOCK);

        assertEquals(a.messageId(), b.messageId());
        assertEquals(new BinaryProtocolCodec().decodeCommand(new BinaryProtocolCodec().encodeCommand(a)), a);
    }

    @Test
    void completionAndEventSequencesRemainDistinctAcrossRoundTrip() {
        UUID matchId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID playerId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        CompletionObservation observation = new CompletionObservation(matchId, 2, 8, 1, playerId, 6, 1200);

        MatchEvent event = MatchMessages.taskCompleted(observation, 15, CLOCK);
        MatchEvent decoded = new BinaryProtocolCodec().decodeEvent(new BinaryProtocolCodec().encodeEvent(event));

        assertEquals(15, decoded.seq());
        assertEquals(observation, MatchMessages.completionObservation(decoded));
        assertNotEquals(observation.seq(), decoded.seq());
    }

    @Test
    void scoreTransactionIdsAreStableButNamespacedBySequence() {
        UUID first = DeterministicIds.scoreTransaction(new UUID(0, 2), 1, 7, new UUID(0, 1), "cell:0");
        UUID replay = DeterministicIds.scoreTransaction(new UUID(0, 2), 1, 7, new UUID(0, 1), "cell:0");
        UUID next = DeterministicIds.scoreTransaction(new UUID(0, 2), 1, 8, new UUID(0, 1), "cell:0");

        assertEquals(first, replay);
        assertNotEquals(first, next);
        assertEquals(5, first.version());
    }

    @Test
    void lifecycleRejectsBackwardsTransitionsAndCanResumeItsPreviousState() {
        MatchStateMachine lifecycle = new MatchStateMachine();
        lifecycle.transitionTo(MatchState.PREPARING);
        lifecycle.transitionTo(MatchState.READY);
        lifecycle.transitionTo(MatchState.ROUTING);
        lifecycle.transitionTo(MatchState.SUSPENDED);

        assertEquals(MatchState.ROUTING, lifecycle.resume().to());
        assertThrows(IllegalStateException.class, () -> lifecycle.transitionTo(MatchState.READY));
        lifecycle.transitionTo(MatchState.ABORTED);
        assertTrue(lifecycle.state().terminal());
        assertThrows(IllegalStateException.class, () -> lifecycle.transitionTo(MatchState.PREPARING));
    }
}

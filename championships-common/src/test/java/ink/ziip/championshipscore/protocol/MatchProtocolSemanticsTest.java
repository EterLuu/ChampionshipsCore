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
    void terminalStateCannotMoveBackwards() {
        MatchStateMachine machine = new MatchStateMachine();
        machine.transitionTo(MatchState.ABORTED);

        assertThrows(IllegalStateException.class, () -> machine.transitionTo(MatchState.PREPARING));
    }
}

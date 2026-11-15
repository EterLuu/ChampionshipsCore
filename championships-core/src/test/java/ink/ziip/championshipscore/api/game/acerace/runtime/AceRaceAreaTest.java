package ink.ziip.championshipscore.api.game.acerace.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class AceRaceAreaTest {

    @Test
    void completionUsesOnlyTheNonemptyCurrentRoster() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID departed = UUID.randomUUID();
        assertFalse(AceRaceArea.allParticipantsFinished(List.of(first, second), List.of(first)));
        assertTrue(
                AceRaceArea.allParticipantsFinished(
                        List.of(first, second), List.of(first, second)));
        assertTrue(
                AceRaceArea.allParticipantsFinished(
                        List.of(first, first), List.of(departed, first)));
        assertFalse(AceRaceArea.allParticipantsFinished(List.of(), List.of(departed)));
    }
}

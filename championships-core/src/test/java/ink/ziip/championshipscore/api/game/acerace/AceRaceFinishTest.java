package ink.ziip.championshipscore.api.game.acerace;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AceRaceFinishTest {

    @Test
    void endsOnlyAfterEveryCurrentParticipantHasFinished() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertFalse(AceRaceArea.allParticipantsFinished(List.of(first, second), List.of(first)));
        assertTrue(AceRaceArea.allParticipantsFinished(List.of(first, second), List.of(first, second)));
    }

    @Test
    void removedOrDuplicateRosterEntriesDoNotPreventCompletion() {
        UUID current = UUID.randomUUID();
        UUID departed = UUID.randomUUID();

        assertTrue(AceRaceArea.allParticipantsFinished(
                List.of(current, current), List.of(departed, current)));
        assertFalse(AceRaceArea.allParticipantsFinished(List.of(), List.of(departed)));
    }
}

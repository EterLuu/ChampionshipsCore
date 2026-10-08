package ink.ziip.championshipscore.api.game.laserbox.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class LaserBoxKillFeedbackTest {

    @Test
    void singleKillsUseTheExpectedNotesAndRejectOutOfRangeCounts() {
        assertEquals(
                List.of(10, 12, 13, 15),
                List.of(
                        (int) LaserBoxKillFeedback.noteForKills(1).getId(),
                        (int) LaserBoxKillFeedback.noteForKills(2).getId(),
                        (int) LaserBoxKillFeedback.noteForKills(3).getId(),
                        (int) LaserBoxKillFeedback.noteForKills(4).getId()));
        assertThrows(IllegalArgumentException.class, () -> LaserBoxKillFeedback.noteForKills(0));
        assertThrows(IllegalArgumentException.class, () -> LaserBoxKillFeedback.noteForKills(5));
    }

    @Test
    void aceIsLayeredEEEThenG() {
        assertEquals(
                List.of(10, 10, 10, 13),
                LaserBoxKillFeedback.aceMelody().stream().map(note -> (int) note.getId()).toList());
    }
}

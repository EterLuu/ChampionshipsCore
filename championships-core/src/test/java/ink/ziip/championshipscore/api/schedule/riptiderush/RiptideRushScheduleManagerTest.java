package ink.ziip.championshipscore.api.schedule.riptiderush;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class RiptideRushScheduleManagerTest {
    @Test
    void singleMapStillPlaysThreeRounds() {
        assertEquals(
                List.of("raft", "raft", "raft"),
                RiptideRushScheduleManager.roundMaps(List.of("raft")));
    }

    @Test
    void rotatesSelectedMapsForExactlyThreeRounds() {
        assertEquals(
                List.of("a", "b", "a"), RiptideRushScheduleManager.roundMaps(List.of("a", "b")));
        assertEquals(
                List.of("a", "b", "c"),
                RiptideRushScheduleManager.roundMaps(List.of("a", "b", "c", "d")));
    }

    @Test
    void mapsAreFrozenBeforeConfigurationChanges() {
        var configured = new ArrayList<>(List.of("raft"));
        var rounds = RiptideRushScheduleManager.roundMaps(configured);
        configured.clear();
        assertEquals(List.of("raft", "raft", "raft"), rounds);
        assertThrows(UnsupportedOperationException.class, () -> rounds.set(0, "other"));
    }

    @Test
    void noMapsCannotStart() {
        assertTrue(RiptideRushScheduleManager.roundMaps(List.of()).isEmpty());
    }
}

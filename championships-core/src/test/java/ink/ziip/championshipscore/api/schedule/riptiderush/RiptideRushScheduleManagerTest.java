package ink.ziip.championshipscore.api.schedule.riptiderush;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class RiptideRushScheduleManagerTest {
    @Test
    void onlyIntermediateRoundsSkipTheSeparateResultDisplayWindow() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var schedule =
                (RiptideRushScheduleManager)
                        ((sun.misc.Unsafe) field.get(null))
                                .allocateInstance(RiptideRushScheduleManager.class);
        assertEquals(0L, schedule.resultDisplayTicks(true));
        assertEquals(200L, schedule.resultDisplayTicks(false));
    }

    @Test
    void roundSelectionCyclesUpToThreeMapsAndLeavesAnEmptySelectionEmpty() {
        var examples =
                Map.of(
                        List.<String>of(), List.<String>of(),
                        List.of("raft"), List.of("raft", "raft", "raft"),
                        List.of("a", "b"), List.of("a", "b", "a"),
                        List.of("a", "b", "c", "d"), List.of("a", "b", "c"));
        for (var example : examples.entrySet()) {
            assertEquals(
                    example.getValue(),
                    RiptideRushScheduleManager.roundMaps(example.getKey()),
                    "selected=" + example.getKey());
        }
    }

    @Test
    void mapsAreFrozenBeforeConfigurationChanges() {
        var configured = new ArrayList<>(List.of("raft"));
        var rounds = RiptideRushScheduleManager.roundMaps(configured);
        configured.clear();
        assertEquals(List.of("raft", "raft", "raft"), rounds);
        assertThrows(UnsupportedOperationException.class, () -> rounds.set(0, "other"));
    }
}

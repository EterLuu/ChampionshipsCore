package ink.ziip.championshipscore.api.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.List;

class TestEventPlanTest {
    @Test
    void freezesRoundOrderAndNormalizesGameCommand() {
        TestEventPlan plan =
                new TestEventPlan("Smoke", List.of(new TestEventPlan.Round("TNTRun", "astra")));

        assertEquals("Smoke", plan.profile());
        assertEquals("tntrun", plan.rounds().getFirst().game());
        assertEquals("astra", plan.rounds().getFirst().map());
        assertThrows(UnsupportedOperationException.class, () -> plan.rounds().clear());
    }

    @Test
    void rejectsUnknownGamesAndEmptyPlans() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestEventPlan("x", List.of(new TestEventPlan.Round("unknown", null))));
        assertThrows(IllegalArgumentException.class, () -> new TestEventPlan("x", List.of()));
    }
}

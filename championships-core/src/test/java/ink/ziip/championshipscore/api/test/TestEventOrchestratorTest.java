package ink.ziip.championshipscore.api.test;

import static ink.ziip.championshipscore.support.TestObjects.*;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;
import ink.ziip.championshipscore.api.game.tntrun.runtime.TNTRunTeamArea;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class TestEventOrchestratorTest {
    @Test
    void eightCopiesAreOneRoundAndOldOrDuplicateCompletionsCannotAdvanceAgain() throws Exception {
        List<Integer> next = new ArrayList<>();
        AtomicInteger complete = new AtomicInteger();
        var plan =
                new TestEventPlan(
                        "pairs",
                        List.of(
                                new TestEventPlan.Round("battlebox", "map"),
                                new TestEventPlan.Round("battlebox", "map")));
        var orchestrator = new TestEventOrchestrator(plan, next::add, complete::incrementAndGet);
        UUID player = UUID.randomUUID();
        List<BaseGameInstance> copies = new ArrayList<>();
        for (int i = 0; i < 8; i++) copies.add(instance(player, 10D));
        orchestrator.markRoundStarted(0, copies);
        for (int i = 0; i < 7; i++) {
            orchestrator.onRoundComplete(0, copies.get(i));
            orchestrator.onRoundComplete(0, copies.get(i));
        }
        assertTrue(next.isEmpty());
        assertEquals(70D, orchestrator.standings().get(player));
        orchestrator.onRoundComplete(0, instance(player, 999D));
        orchestrator.onRoundComplete(0, copies.get(7));
        orchestrator.onRoundComplete(0, copies.get(7));
        assertEquals(List.of(1), next);
        assertEquals(80D, orchestrator.standings().get(player));
        orchestrator.markRoundStarted(1, copies);
        orchestrator.onRoundComplete(0, copies.get(0));
        assertEquals(80D, orchestrator.standings().get(player));
        for (var copy : copies) orchestrator.onRoundComplete(1, copy);
        assertEquals(1, complete.get());
        assertEquals(160D, orchestrator.standings().get(player));
        assertEquals(List.of(1), next);
    }

    @Test
    void cancelledOrUnstartedRoundCannotRecordScores() throws Exception {
        var plan = new TestEventPlan("x", List.of(new TestEventPlan.Round("tntrun", "map")));
        var o = new TestEventOrchestrator(plan, i -> fail("next"), () -> fail("complete"));
        UUID player = UUID.randomUUID();
        var area = instance(player, 10D);
        o.onRoundComplete(0, area);
        assertTrue(o.standings().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> o.markRoundStarted(0, List.of()));
        o.markRoundStarted(0, List.of(area));
        assertThrows(IllegalStateException.class, () -> o.markRoundStarted(1, List.of(area)));
        o.cancel();
        o.onRoundComplete(0, area);
        assertTrue(o.standings().isEmpty());
        assertThrows(IllegalStateException.class, () -> o.markRoundStarted(0, List.of(area)));
    }

    private static BaseGameInstance instance(UUID player, double points) throws Exception {
        var area = allocate(TNTRunTeamArea.class);
        set(area, "playerPoints", new HashMap<>(Map.of(player, points)));
        return area;
    }
}

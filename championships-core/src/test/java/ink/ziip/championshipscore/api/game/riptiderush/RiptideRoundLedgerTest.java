package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RiptideRoundLedgerTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
    private final RiptideRoundLedger ledger = new RiptideRoundLedger();

    @Test void neverStartedPlayersDoNotAwardSurvivalOrPlacementPoints() {
        ledger.start(List.of(a,b));
        ledger.eliminate(List.of(c));
        assertTrue(ledger.flush().isEmpty());
        assertEquals(Map.of(a,100,b,100),ledger.placementBonuses());
        assertEquals(Set.of(a,b),Set.copyOf(ledger.winners()));
    }

    @Test void allAbsentCannotProduceAWinner() {
        ledger.start(List.of());
        ledger.eliminate(List.of(a,b,c));
        assertTrue(ledger.flush().isEmpty());
        assertTrue(ledger.placementBonuses().isEmpty());
        assertTrue(ledger.winners().isEmpty());
    }

    @Test void separateFailureSourcesInOneTickStillFormOneBatch() {
        ledger.start(List.of(a,b,c));
        ledger.eliminate(List.of(a)); // accepted movement: fell off
        ledger.eliminate(List.of(b)); // tick fallback: disconnected or failed a gate
        assertEquals(Map.of(c,8),ledger.flush());
        assertEquals(Map.of(a,70,b,70,c,100),ledger.placementBonuses());
        assertEquals(List.of(c),ledger.winners());
    }

    @Test void lastTwoFallingTogetherAreBothEliminatedBeforePickingWinners() {
        ledger.start(List.of(a,b));
        ledger.eliminate(List.of(a));
        assertThrows(IllegalStateException.class,ledger::winners);
        ledger.eliminate(List.of(b));
        assertTrue(ledger.flush().isEmpty());
        assertEquals(Map.of(a,100,b,100),ledger.placementBonuses());
        assertEquals(Set.of(a,b),Set.copyOf(ledger.winners()));
    }

    @Test void repeatedDisconnectDeathAndFallDoNotDuplicateAwards() {
        ledger.start(List.of(a,b));
        ledger.eliminate(List.of(a,a));
        ledger.eliminate(List.of(a));
        assertEquals(Map.of(b,4),ledger.flush());
        ledger.eliminate(List.of(a));
        assertTrue(ledger.flush().isEmpty());
        assertEquals(Map.of(a,70,b,100),ledger.placementBonuses());
    }

    @Test void threeRoundsHaveIndependentScoresAndRestorePreviousLosers() {
        Map<UUID,Integer> total = new HashMap<>();
        for (int round=1;round<=3;round++) {
            ledger.start(List.of(a,b,c));
            ledger.eliminate(List.of(a));
            ledger.flush().forEach((id,points)->total.merge(id,points,Integer::sum));
            ledger.eliminate(List.of(b));
            ledger.flush().forEach((id,points)->total.merge(id,points,Integer::sum));
            assertEquals(Map.of(a,30,b,70,c,100),ledger.placementBonuses());
            ledger.placementBonuses().forEach((id,points)->total.merge(id,points,Integer::sum));
            ledger.clear();
            assertTrue(ledger.placementBonuses().isEmpty());
            assertTrue(ledger.flush().isEmpty());
        }
        assertEquals(Map.of(a,90,b,222,c,324),total);
    }

    @Test void absentPlayerMayStartNextRoundWithoutPastRewards() {
        ledger.start(List.of(a,b));
        ledger.eliminate(List.of(b));
        ledger.flush();
        ledger.start(List.of(a,b,c));
        assertTrue(ledger.flush().isEmpty());
        assertEquals(Map.of(a,100,b,100,c,100),ledger.placementBonuses());
    }
}

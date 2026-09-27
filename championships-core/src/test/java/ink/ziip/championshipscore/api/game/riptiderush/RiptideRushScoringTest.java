package ink.ziip.championshipscore.api.game.riptiderush;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RiptideRushScoringTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();

    @Test void distinctEliminationsKeepOrdinaryPodium() {
        assertEquals(Map.of(a,100,b,70,c,30), RiptideRushScoring.placementBonuses(
                List.of(a), List.of(List.of(d), List.of(c), List.of(b))));
    }

    @Test void sameBatchSharesSecondAndOccupiesThird() {
        assertEquals(Map.of(a,100,b,70,c,70), RiptideRushScoring.placementBonuses(
                List.of(a), List.of(List.of(d),List.of(b,c))));
    }

    @Test void entireLastBatchWinsWhenNobodySurvivesRegardlessOfOrder() {
        var expected = Map.of(a,100,b,100,c,30);
        assertEquals(expected, RiptideRushScoring.placementBonuses(List.of(),List.of(List.of(c),List.of(a,b))));
        assertEquals(expected, RiptideRushScoring.placementBonuses(List.of(),List.of(List.of(c),List.of(b,a))));
    }

    @Test void twoSurvivorsOccupyFirstAndSecond() {
        assertEquals(Map.of(a,100,b,100,c,30), RiptideRushScoring.placementBonuses(
                List.of(a,b),List.of(List.of(d),List.of(c))));
    }

    @Test void threeOrMoreSurvivorsLeaveNoOtherPodiumPlaces() {
        assertEquals(Map.of(a,100,b,100,c,100), RiptideRushScoring.placementBonuses(List.of(a,b,c),List.of(List.of(d))));
        assertEquals(Map.of(a,100,b,100,c,100,d,100), RiptideRushScoring.placementBonuses(List.of(a,b,c,d),List.of()));
    }

    @Test void nobodyEnteredMeansNoPoints() {
        assertTrue(RiptideRushScoring.placementBonuses(List.of(),List.of()).isEmpty());
    }
}

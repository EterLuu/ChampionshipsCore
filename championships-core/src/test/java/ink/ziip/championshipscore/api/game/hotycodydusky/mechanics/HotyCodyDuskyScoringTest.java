package ink.ziip.championshipscore.api.game.hotycodydusky.mechanics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class HotyCodyDuskyScoringTest {
    @Test
    void survivorsShareFirstAcrossAllPhysicalCopiesWithTheOriginalDenseRanks() {
        UUID a = UUID.randomUUID(),
                b = UUID.randomUUID(),
                c = UUID.randomUUID(),
                d = UUID.randomUUID(),
                e = UUID.randomUUID();
        var awards =
                HotyCodyDuskyScoring.rankingBonuses(
                        Map.of(c, 30L, d, 20L, e, 10L), List.of(a, b), 40);
        assertEquals(Map.of(a, 25, b, 25, c, 20, d, 15), awards);
    }

    @Test
    void theWholeInstanceHasOnlyOneRankingWhenAllPlayersHaveDied() {
        UUID a = UUID.randomUUID(),
                b = UUID.randomUUID(),
                c = UUID.randomUUID(),
                d = UUID.randomUUID();
        var deaths = Map.of(a, 40L, b, 30L, c, 30L, d, 20L);
        assertEquals(
                Map.of(a, 25, b, 20, c, 20, d, 15),
                HotyCodyDuskyScoring.rankingBonuses(deaths, List.of(), 50));
        assertEquals(4, deaths.size());
    }
}

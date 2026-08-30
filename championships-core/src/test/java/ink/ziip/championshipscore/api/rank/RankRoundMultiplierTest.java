package ink.ziip.championshipscore.api.rank;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankRoundMultiplierTest {
    @Test
    void resolvesOneBasedConfiguredRoundsAndRejectsOutOfRangeRounds() {
        List<Double> multipliers = List.of(0.75D, 1D, 2.25D);

        assertEquals(0D, RankManager.configuredPointMultiple(0, multipliers));
        assertEquals(0.75D, RankManager.configuredPointMultiple(1, multipliers));
        assertEquals(1D, RankManager.configuredPointMultiple(2, multipliers));
        assertEquals(2.25D, RankManager.configuredPointMultiple(3, multipliers));
        assertEquals(0D, RankManager.configuredPointMultiple(4, multipliers));
    }
}

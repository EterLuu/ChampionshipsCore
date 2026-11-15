package ink.ziip.championshipscore.shared.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.List;

class RankingWindowTest {
    @Test
    void rankingWindowAddsOnlyAnOutOfRangeViewer() {
        List<Integer> ranked = List.of(1, 2, 3, 4, 5);
        assertEquals(List.of(1, 2, 3, 5), RankingWindow.select(ranked, 5, 3));
        assertEquals(List.of(1, 2, 3), RankingWindow.select(ranked, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> RankingWindow.select(ranked, 2, -1));
    }
}

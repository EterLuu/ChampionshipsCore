package ink.ziip.championshipscore.presentation.sidebar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.List;

class CoreSidebarManagerTest {
    @Test
    void selectsTopEightAndAppendsOutOfRangeViewerLikeBingo() {
        List<Integer> ranked = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        assertEquals(
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 10),
                CoreSidebarManager.selectRankingRows(ranked, 10));
        assertEquals(
                List.of(1, 2, 3, 4, 5, 6, 7, 8), CoreSidebarManager.selectRankingRows(ranked, 3));
    }
}

package ink.ziip.championshipscore.shared.presentation;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SharedPresentationPolicyTest {
    @Test
    void ordinaryRulesKeepTenSecondCadenceInLongIntroductions() {
        assertEquals(0, RuleIntroductionTimeline.sectionAt(10, 90, 3));
        assertEquals(1, RuleIntroductionTimeline.sectionAt(20, 90, 3));
        assertEquals(2, RuleIntroductionTimeline.sectionAt(30, 90, 3));
        assertEquals(-1, RuleIntroductionTimeline.sectionAt(49, 90, 3));
    }

    @Test
    void longRuleListsCompressToFit() {
        for (int section = 0; section < 8; section++) {
            assertEquals(section, RuleIntroductionTimeline.sectionAt(10 + section * 4, 45, 8));
        }
    }

    @Test
    void rankingWindowAddsOnlyAnOutOfRangeViewer() {
        List<Integer> ranked = List.of(1, 2, 3, 4, 5);
        assertEquals(List.of(1, 2, 3, 5), RankingWindow.select(ranked, 5, 3));
        assertEquals(List.of(1, 2, 3), RankingWindow.select(ranked, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> RankingWindow.select(ranked, 2, -1));
    }

    @Test
    void formatsCountdownAsMinutesAndSeconds() {
        assertEquals("00:00", DurationText.minutesSeconds(-1));
        assertEquals("00:07", DurationText.minutesSeconds(7));
        assertEquals("09:59", DurationText.minutesSeconds(599));
        assertEquals("12:56", DurationText.minutesSeconds(12 * 60L + 56));
    }
}

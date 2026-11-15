package ink.ziip.championshipscore.shared.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuleIntroductionTimelineTest {
    @ParameterizedTest(name = "second {0} of {1}s with {2} sections -> {3}")
    @CsvSource({
        "9, 45, 3, -1",
        "10, 45, 3, 0",
        "20, 45, 3, 1",
        "30, 45, 3, 2",
        "40, 45, 3, -1",
        "10, 90, 3, 0",
        "20, 90, 3, 1",
        "30, 90, 3, 2",
        "49, 90, 3, -1"
    })
    void standardSectionsStartAtTenSecondsWithTheNormalCadence(
            int elapsed, int duration, int sections, int expected) {
        assertEquals(expected, RuleIntroductionTimeline.sectionAt(elapsed, duration, sections));
    }

    @Test
    void longRuleListsAreCompressedToFitTheIntroduction() {
        for (int section = 0; section < 8; section++) {
            assertEquals(
                    section,
                    RuleIntroductionTimeline.sectionAt(10 + section * 4, 45, 8),
                    "section " + section);
        }
    }
}

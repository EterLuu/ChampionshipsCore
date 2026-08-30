package ink.ziip.championshipscore.shared.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationTextTest {
    @Test
    void formatsCountdownAsMinutesAndSeconds() {
        assertEquals("00:00", DurationText.minutesSeconds(-1));
        assertEquals("00:07", DurationText.minutesSeconds(7));
        assertEquals("09:59", DurationText.minutesSeconds(599));
        assertEquals("12:56", DurationText.minutesSeconds(12 * 60L + 56));
    }
}

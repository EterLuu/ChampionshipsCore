package ink.ziip.championshipscore.api.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MuteDurationTest {
    @ParameterizedTest
    @CsvSource({"1h30m, 5400000", "1d1h1m1s, 90061000", "1W, 604800000"})
    void combinedUnitsParseToMilliseconds(String input, long millis) {
        assertEquals(millis, MuteDuration.parseMillis(input));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "0s",
                "30",
                "-1m",
                "1.5h",
                "1hgarbage",
                " 1h",
                "1h 30m",
                "9223372036854775807w",
                "999999999999999999999999999s"
            })
    void partialZeroAndOverflowInputIsRejected(String input) {
        assertThrows(IllegalArgumentException.class, () -> MuteDuration.parseMillis(input));
    }

    @ParameterizedTest
    @CsvSource({"5400000, 1h30m", "1, 1s"})
    void formatsCompactDurationsAndRoundsSubSecondUp(long millis, String expected) {
        assertEquals(expected, MuteDuration.formatMillis(millis));
    }
}

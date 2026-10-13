package ink.ziip.championshipscore.shared.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.Map;

class DurationTextTest {
    @Test
    void bossBarsSwitchToMinutesOnlyAboveSixtySecondsAndKeepZeroSecondRemainders() {
        var examples =
                Map.of(
                        0L, "0秒", 7L, "7秒", 59L, "59秒", 60L, "60秒", 61L, "1分1秒", 120L, "2分0秒", 700L,
                        "11分40秒", 3600L, "60分0秒");
        examples.forEach(
                (seconds, expected) ->
                        assertEquals(
                                expected,
                                DurationText.bossBarSeconds(seconds),
                                seconds.toString()));
    }

    @Test
    void bossBarsClampNegativeDurationsAndSupportLongElapsedClocksWithoutOverflow() {
        assertEquals("0秒", DurationText.bossBarSeconds(Long.MIN_VALUE));
        assertEquals("35791394分7秒", DurationText.bossBarSeconds(Integer.MAX_VALUE));
        assertEquals("153722867280912930分7秒", DurationText.bossBarSeconds(Long.MAX_VALUE));
    }

    @Test
    void localizedBossBarsUseTheSameThresholdAndOrdinaryClocksKeepTheirOriginalFormat() {
        assertEquals("60s", DurationText.bossBarSeconds(60, "%seconds%s", "%minutes%m %seconds%s"));
        assertEquals(
                "1m 1s", DurationText.bossBarSeconds(61, "%seconds%s", "%minutes%m %seconds%s"));
        assertEquals(
                "11m 40s", DurationText.bossBarSeconds(700, "%seconds%s", "%minutes%m %seconds%s"));
        assertEquals("11:40", DurationText.minutesSeconds(700));
    }
}

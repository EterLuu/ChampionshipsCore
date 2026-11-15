package ink.ziip.championshipscore.api.game.decarnival.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DragonEggCarnivalRulesTest {
    @ParameterizedTest(name = "death {0} -> {1}s")
    @CsvSource({"1, 5", "2, 10", "3, 15", "6, 30", "12, 30"})
    void respawnDelayIncreasesByFiveSecondsAndCapsAtThirty(int deaths, int seconds) {
        assertEquals(seconds, DragonEggCarnivalArea.respawnDelaySeconds(deaths));
    }

    @Test
    void platformsMirrorTheVanillaArrivalPlatform() {
        assertEquals(100, DragonEggCarnivalArea.platformCenterX(true));
        assertEquals(-100, DragonEggCarnivalArea.platformCenterX(false));
    }

    @Test
    void victoryNeedsTwoOfTheThreeVanillaEndAdvancements() {
        // Advancement keys are matched against vanilla events; a typo silently prevents wins.
        assertEquals(2, DragonEggCarnivalArea.ADVANCEMENTS_TO_WIN);
        assertEquals(
                java.util.Set.of("end/kill_dragon", "end/dragon_egg", "end/enter_end_gateway"),
                DragonEggCarnivalArea.VICTORY_ADVANCEMENTS);
    }

    @ParameterizedTest(name = "damage {0} -> {1} of {2} crosses {3}")
    @CsvSource({"0, 39.99, 200, 0", "39.99, 40, 200, 1", "35, 121, 200, 3"})
    void dragonPressureTriggersOncePerCumulativeTwentyPercent(
            double before, double after, double maxHealth, int thresholds) {
        assertEquals(
                thresholds,
                DragonEggCarnivalArea.crossedDragonDamageThresholds(before, after, maxHealth));
    }
}

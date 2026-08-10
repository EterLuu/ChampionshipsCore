package ink.ziip.championshipscore.api.daily;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyRulesTest {
    @Test
    void clampsInvalidConfigurationToUsableCapacity() {
        DailyRules rules = new DailyRules(99, 99, 2, 3, 0);

        assertEquals(6, rules.maxPlayers());
        assertEquals(6, rules.minPlayers());
        assertEquals(3, rules.countdownSeconds());
    }
}

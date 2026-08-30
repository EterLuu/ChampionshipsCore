package ink.ziip.championshipscore.api.game.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameManagerSpectatorPolicyTest {
    @Test
    void strictRosterRestrictionOnlyAppliesOutsideDailyMode() {
        assertFalse(GameManager.shouldEnforceStrictSpectatorRule(false, false));
        assertFalse(GameManager.shouldEnforceStrictSpectatorRule(false, true));
        assertTrue(GameManager.shouldEnforceStrictSpectatorRule(true, false));
        assertFalse(GameManager.shouldEnforceStrictSpectatorRule(true, true));
    }
}

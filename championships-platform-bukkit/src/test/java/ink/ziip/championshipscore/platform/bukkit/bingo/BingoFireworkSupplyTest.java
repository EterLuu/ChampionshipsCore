package ink.ziip.championshipscore.platform.bukkit.bingo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BingoFireworkSupplyTest {
    @Test
    void firstRefillWaitsTwentySecondsAndDuplicateTicksNeverAddSupplies() {
        var supply = new BingoFireworkSupply();
        assertFalse(supply.shouldRefill(0));
        assertFalse(supply.shouldRefill(19));
        assertTrue(supply.shouldRefill(20));
        assertFalse(supply.shouldRefill(20));
        assertFalse(supply.shouldRefill(21));
        assertFalse(supply.shouldRefill(39));
        assertTrue(supply.shouldRefill(40));
    }

    @Test
    void delayedTicksGrantOneRocketWithoutCatchingUpMissedWindows() {
        var supply = new BingoFireworkSupply();
        assertTrue(supply.shouldRefill(61));
        assertFalse(supply.shouldRefill(61));
        assertFalse(supply.shouldRefill(79));
        assertTrue(supply.shouldRefill(80));
        assertFalse(supply.shouldRefill(20));
        assertFalse(supply.shouldRefill(-1));
    }

    @Test
    void twelveMinuteRoundHasIndependentRefillsEachRound() {
        for (int round = 0; round < 2; round++) {
            var supply = new BingoFireworkSupply();
            int refills = 0;
            for (int elapsed = 0; elapsed < 720; elapsed++) {
                if (supply.shouldRefill(elapsed)) refills++;
                assertFalse(supply.shouldRefill(elapsed));
            }
            assertEquals(35, refills);
        }
    }
}

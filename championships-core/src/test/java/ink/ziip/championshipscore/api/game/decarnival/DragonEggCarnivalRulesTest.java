package ink.ziip.championshipscore.api.game.decarnival;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonEggCarnivalRulesTest {
    @Test
    void respawnDelayIncreasesByFiveSecondsAndCapsAtThirty() {
        assertEquals(5, DragonEggCarnivalArea.respawnDelaySeconds(1));
        assertEquals(10, DragonEggCarnivalArea.respawnDelaySeconds(2));
        assertEquals(15, DragonEggCarnivalArea.respawnDelaySeconds(3));
        assertEquals(30, DragonEggCarnivalArea.respawnDelaySeconds(6));
        assertEquals(30, DragonEggCarnivalArea.respawnDelaySeconds(12));
    }

    @Test
    void platformsMirrorTheVanillaArrivalPlatform() {
        assertEquals(100, DragonEggCarnivalArea.platformCenterX(true));
        assertEquals(-100, DragonEggCarnivalArea.platformCenterX(false));
        assertEquals(48, DragonEggCarnivalArea.PLATFORM_FLOOR_Y);
        assertEquals(2, DragonEggCarnivalArea.PLATFORM_RADIUS);
        assertEquals(3, DragonEggCarnivalArea.PLATFORM_AIR_HEIGHT);
    }

    @Test
    void crystalRewardGivesTwoPearlsPerPlayer() {
        assertEquals(2, DragonEggCarnivalArea.CRYSTAL_PEARLS_PER_PLAYER);
    }

    @Test
    void dragonPressureTriggersOncePerCumulativeTwentyPercent() {
        assertEquals(0, DragonEggCarnivalArea.crossedDragonDamageThresholds(0, 39.99, 200));
        assertEquals(1, DragonEggCarnivalArea.crossedDragonDamageThresholds(39.99, 40, 200));
        assertEquals(3, DragonEggCarnivalArea.crossedDragonDamageThresholds(35, 121, 200));
    }

    @Test
    void victoryUsesTheThreeRequestedAdvancements() {
        assertEquals(2, DragonEggCarnivalArea.ADVANCEMENTS_TO_WIN);
        assertEquals(3, DragonEggCarnivalArea.VICTORY_ADVANCEMENTS.size());
        assertTrue(DragonEggCarnivalArea.VICTORY_ADVANCEMENTS.contains("end/kill_dragon"));
        assertTrue(DragonEggCarnivalArea.VICTORY_ADVANCEMENTS.contains("end/dragon_egg"));
        assertTrue(DragonEggCarnivalArea.VICTORY_ADVANCEMENTS.contains("end/enter_end_gateway"));
    }
}

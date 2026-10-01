package ink.ziip.championshipscore.api.schedule.frostbite;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrostbiteScheduleManagerTest {
    @Test
    void frostbiteUsesThreeRoundsLikeSnowballShowdown() {
        assertEquals(List.of("glacial_keep", "frosty_fjord", "glacial_keep"),
                FrostbiteScheduleManager.roundMaps(List.of("glacial_keep", "frosty_fjord")));
    }
}

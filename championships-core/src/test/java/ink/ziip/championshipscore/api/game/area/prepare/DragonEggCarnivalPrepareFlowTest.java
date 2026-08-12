package ink.ziip.championshipscore.api.game.area.prepare;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonEggCarnivalPrepareFlowTest {
    @Test
    void requiredFightRegionIncludesBothPlatformsAltarAndGateways() {
        assertTrue(DragonEggCarnivalPrepareFlow.coversRequiredFightRegion(
                new Vector(-130, 0, -168), new Vector(193, 137, 138)));
        assertFalse(DragonEggCarnivalPrepareFlow.coversRequiredFightRegion(
                new Vector(-80, 0, -80), new Vector(80, 128, 80)));
        assertFalse(DragonEggCarnivalPrepareFlow.coversRequiredFightRegion(
                new Vector(-104, 40, -104), new Vector(104, 128, 104)));
    }
}

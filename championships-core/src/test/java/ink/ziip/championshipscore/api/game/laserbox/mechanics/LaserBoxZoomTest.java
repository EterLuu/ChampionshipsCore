package ink.ziip.championshipscore.api.game.laserbox.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LaserBoxZoomTest {
    @Test
    void scopedFovUsesTheStrongerScopeMultiplier() {
        assertEquals(1.2F, LaserBoxZoom.scopedFovDenominator(0.1F), 1.0E-6F);
        assertEquals(0.0F, LaserBoxZoom.scopedFovDenominator(0.0F), 1.0E-6F);
    }
}

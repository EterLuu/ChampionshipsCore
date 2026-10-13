package ink.ziip.championshipscore.api.game.laser.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LaserZoomTest {
    @Test
    void scopedFovUsesTheStrongerScopeMultiplier() {
        assertEquals(2.0F, LaserZoom.scopedFovDenominator(0.1F), 1.0E-6F);
        assertEquals(0.0F, LaserZoom.scopedFovDenominator(0.0F), 1.0E-6F);
    }
}

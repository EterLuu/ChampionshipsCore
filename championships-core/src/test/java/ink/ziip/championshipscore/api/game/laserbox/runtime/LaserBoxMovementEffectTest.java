package ink.ziip.championshipscore.api.game.laserbox.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LaserBoxMovementEffectTest {
    @Test
    void sniperUsesSlownessOneWhileHeldAndSlownessTwoWhileAiming() {
        assertEquals(0, LaserBoxArea.sniperSlownessAmplifier(false));
        assertEquals(1, LaserBoxArea.sniperSlownessAmplifier(true));
    }
}

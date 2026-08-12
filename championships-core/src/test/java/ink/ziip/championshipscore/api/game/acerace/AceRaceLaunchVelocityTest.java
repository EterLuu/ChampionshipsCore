package ink.ziip.championshipscore.api.game.acerace;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AceRaceLaunchVelocityTest {

    @Test
    void keepsRedAndOrangePadBaseMomentumDistinct() {
        assertEquals(2D, AceRaceArea.launchHorizontalVelocity(Material.RED_WOOL));
        assertEquals(0.75D, AceRaceArea.launchBaseVerticalVelocity(Material.RED_WOOL));
        assertEquals(4D, AceRaceArea.launchHorizontalVelocity(Material.ORANGE_WOOL));
        assertEquals(1.5D, AceRaceArea.launchBaseVerticalVelocity(Material.ORANGE_WOOL));
    }

    @Test
    void clampsDownwardAimToUsefulMinimum() {
        assertEquals(0.8D, AceRaceArea.calculateAimedVerticalVelocity(1D, -1D));
    }

    @Test
    void preservesBaseLiftAtLevelAim() {
        assertEquals(1D, AceRaceArea.calculateAimedVerticalVelocity(1D, 0D));
    }

    @Test
    void clampsUpwardAimToMaximum() {
        assertEquals(1.2D, AceRaceArea.calculateAimedVerticalVelocity(1D, 1D));
    }

    @Test
    void retainsPitchScalingBetweenBounds() {
        assertEquals(1.1D, AceRaceArea.calculateAimedVerticalVelocity(1D, 0.1D));
    }

}

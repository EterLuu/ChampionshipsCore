package ink.ziip.championshipscore.api.game.acerace;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AceRaceRulesTest {
    @Test
    void parsesSegmentEquipment() {
        assertEquals(AceRaceEquipment.DOLPHINS_GRACE,
                AceRaceEquipment.fromConfig("dolphins_grace"));
        assertEquals("海豚的恩惠", AceRaceEquipment.DOLPHINS_GRACE.displayName());
    }

    @Test
    void endsOnlyAfterEveryCurrentParticipantHasFinished() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertFalse(AceRaceArea.allParticipantsFinished(List.of(first, second), List.of(first)));
        assertTrue(AceRaceArea.allParticipantsFinished(List.of(first, second), List.of(first, second)));
    }

    @Test
    void removedOrDuplicateRosterEntriesDoNotPreventCompletion() {
        UUID current = UUID.randomUUID();
        UUID departed = UUID.randomUUID();

        assertTrue(AceRaceArea.allParticipantsFinished(
                List.of(current, current), List.of(departed, current)));
        assertFalse(AceRaceArea.allParticipantsFinished(List.of(), List.of(departed)));
    }

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

package ink.ziip.championshipscore.api.game.acerace.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AceRaceEquipmentTest {

    @Test
    void parsesSegmentEquipment() {
        assertEquals(
                AceRaceEquipment.DOLPHINS_GRACE, AceRaceEquipment.fromConfig("dolphins_grace"));
        assertEquals("海豚的恩惠", AceRaceEquipment.DOLPHINS_GRACE.displayName());
    }
}

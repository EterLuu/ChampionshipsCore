package ink.ziip.championshipscore.api.game.acerace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AceRaceEquipmentTest {

    @Test
    void parsesDolphinsGraceAsSegmentEquipment() {
        assertEquals(AceRaceEquipment.DOLPHINS_GRACE,
                AceRaceEquipment.fromConfig("dolphins_grace"));
        assertEquals("海豚的恩惠", AceRaceEquipment.DOLPHINS_GRACE.displayName());
    }
}

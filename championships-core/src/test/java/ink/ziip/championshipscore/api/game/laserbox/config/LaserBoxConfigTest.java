package ink.ziip.championshipscore.api.game.laserbox.config;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LaserBoxConfigTest {
    @Test
    void supplyPointAcceptsTheEditorLocationFormat() {
        assertEquals(
                new Vector(88.5, 91.0, 26.5),
                LaserBoxConfig.parseSupplyPoint(
                        "laserbox:88.5:91.0:26.5:90.135254:2.604332", "laserbox"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "88.5 91.0 26.5",
                "other:88.5:91:26.5:0:0",
                "laserbox:88.5:91:26.5:0",
                "laserbox:NaN:91:26:0:0"
            })
    void supplyPointRejectsLegacyFormatWrongWorldAndInvalidCoordinates(String raw) {
        assertThrows(
                IllegalArgumentException.class,
                () -> LaserBoxConfig.parseSupplyPoint(raw, "laserbox"));
    }
}

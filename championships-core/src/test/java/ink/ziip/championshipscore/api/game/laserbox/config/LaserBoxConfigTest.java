package ink.ziip.championshipscore.api.game.laserbox.config;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class LaserBoxConfigTest {

    @Test
    void acceptsEditorLocationFormatOnly() {
        Vector expected = new Vector(88.5, 91.0, 26.5);
        assertEquals(
                expected,
                LaserBoxConfig.parseSupplyPoint(
                        "laserbox:88.5:91.0:26.5:90.135254:2.604332", "laserbox"));
        assertThrows(
                IllegalArgumentException.class,
                () -> LaserBoxConfig.parseSupplyPoint("88.5 91.0 26.5", "laserbox"));
    }

    @Test
    void rejectsWrongWorldAndInvalidCoordinates() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LaserBoxConfig.parseSupplyPoint("other:88.5:91:26.5:0:0", "laserbox"));
        assertThrows(
                IllegalArgumentException.class,
                () -> LaserBoxConfig.parseSupplyPoint("laserbox:88.5:91:26.5:0", "laserbox"));
        assertThrows(
                IllegalArgumentException.class,
                () -> LaserBoxConfig.parseSupplyPoint("laserbox:NaN:91:26:0:0", "laserbox"));
    }
}

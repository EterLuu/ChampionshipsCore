package ink.ziip.championshipscore.api.game.frostbite.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class FrostbiteConfigTest {
    @Test
    void acceptsCanonicalSpawnAndSupplyPoints() {
        Location spawn =
                FrostbiteConfig.parsePoint(
                        "frostbite_frosty_fjord:1211.5:13:-2313.5:180:0", "frostbite_frosty_fjord");
        assertEquals(1211.5, spawn.getX());
        assertEquals(13, spawn.getY());
        assertEquals(-2313.5, spawn.getZ());
        assertEquals(180F, spawn.getYaw());
        assertEquals(0F, spawn.getPitch());

        Location supply =
                FrostbiteConfig.parsePoint(
                        "frostbite_glacial_keep:199.5:124:933.5:0:0", "frostbite_glacial_keep");
        assertEquals(199.5, supply.getX());
        assertEquals(124, supply.getY());
        assertEquals(933.5, supply.getZ());
        assertEquals(0F, supply.getYaw());
        assertEquals(0F, supply.getPitch());
    }

    @Test
    void preservesNegativeOrientation() {
        Location spawn =
                FrostbiteConfig.parsePoint(
                        "frostbite_glacial_keep:137.5:124:905.5:-130:0", "frostbite_glacial_keep");
        assertEquals(137.5, spawn.getX());
        assertEquals(124, spawn.getY());
        assertEquals(905.5, spawn.getZ());
        assertEquals(-130F, spawn.getYaw());
    }

    @Test
    void acceptsTheEditorLocationFormatAndPreservesOrientation() {
        Location point =
                FrostbiteConfig.parsePoint(
                        "frostbite:88.5:91.0:26.5:90.135254:2.604332", "frostbite");
        assertEquals(88.5, point.getX());
        assertEquals(91.0, point.getY());
        assertEquals(26.5, point.getZ());
        assertEquals(90.135254F, point.getYaw());
        assertEquals(2.604332F, point.getPitch());
    }

    @Test
    void rejectsMalformedPointsAndInvalidValues() {
        for (String raw :
                new String[] {
                    null,
                    "",
                    "88.5 91.0",
                    "1211.5 13 -2313.5 180",
                    "88.5 91.0 26.5 0 0",
                    "NaN 91 26.5",
                    "88.5 Infinity 26.5",
                    "88.5 91 26.5 Infinity",
                    "88.5 91 26.5 invalid",
                    "other:88.5:91:26.5:0:0",
                    "frostbite:88.5:91:26.5:0",
                    "frostbite:NaN:91:26.5:0:0",
                    "frostbite:88.5:91:26.5:Infinity:0"
                }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FrostbiteConfig.parsePoint(raw, "frostbite"));
        }
    }
}

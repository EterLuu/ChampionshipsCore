package ink.ziip.championshipscore.api.game.frostbite.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class FrostbiteConfigTest {
    @Test
    void canonicalPointsPreserveEveryCoordinateAndPositiveNegativeOrFractionalRotation() {
        record Example(String world, String raw, Location expected) {}
        var examples =
                java.util.List.of(
                        new Example(
                                "frostbite_frosty_fjord",
                                "frostbite_frosty_fjord:1211.5:13:-2313.5:180:0",
                                new Location(null, 1211.5, 13, -2313.5, 180F, 0F)),
                        new Example(
                                "frostbite_glacial_keep",
                                "frostbite_glacial_keep:199.5:124:933.5:0:0",
                                new Location(null, 199.5, 124, 933.5, 0F, 0F)),
                        new Example(
                                "frostbite_glacial_keep",
                                "frostbite_glacial_keep:137.5:124:905.5:-130:0",
                                new Location(null, 137.5, 124, 905.5, -130F, 0F)),
                        new Example(
                                "frostbite",
                                "frostbite:88.5:91.0:26.5:90.135254:2.604332",
                                new Location(null, 88.5, 91.0, 26.5, 90.135254F, 2.604332F)));
        for (Example example : examples) {
            assertEquals(
                    example.expected(),
                    FrostbiteConfig.parsePoint(example.raw(), example.world()),
                    example.raw());
        }
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

package ink.ziip.championshipscore.configuration.location;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LocationConfigTest {
    @Test
    void rawCoordinatesLoadWithoutAWorldAndResolveLater() throws Exception {
        var document = new YamlConfiguration();
        document.loadFromString(
                """
                spawn:
                  world_key: minecraft:unloaded_arena
                  x: 1.5
                  y: -64
                  z: 3
                  yaw: 90
                  pitch: -12.5
                """);
        ConfiguredLocation coordinates = ConfiguredLocation.read(document.get("spawn"));
        Location unresolved = coordinates.resolve(identifier -> null);
        assertNull(unresolved.getWorld());
        assertEquals(-64D, unresolved.getY());
        assertEquals(90F, unresolved.getYaw());
        assertEquals(-12.5F, unresolved.getPitch());
        assertEquals("minecraft:unloaded_arena", coordinates.world());
        assertEquals(coordinates, ConfiguredLocation.read(coordinates.asString()));
    }

    @Test
    void savingAnUnloadedLocationRetainsWorldAndAvoidsBukkitSerialization() throws Exception {
        var document = new YamlConfiguration();
        document.loadFromString("spawn: {world: unloaded, x: 1, y: 2, z: 3}\n");
        LocationConfig.write(document, "spawn", new Location(null, 4.5D, -12D, 6D, 45F, -30F));
        String saved = document.saveToString();
        assertFalse(saved.contains("==:"));
        var reloaded = new YamlConfiguration();
        reloaded.loadFromString(saved);
        assertEquals(
                new ConfiguredLocation("unloaded", 4.5D, -12D, 6D, 45F, -30F),
                ConfiguredLocation.read(reloaded.get("spawn")));
        LocationConfig.write(document, "spawn", null);
        assertFalse(document.contains("spawn"));
    }

    @Test
    void bothStoredRepresentationsPreserveCoordinatesAndRotation() throws Exception {
        var document = new YamlConfiguration();
        document.loadFromString(
                "spawn: {world: arena, x: 1.25, y: 2, z: -3, yaw: 90, pitch: 30}\n");
        assertEquals(
                ConfiguredLocation.read("arena:1.25:2:-3:90:30"),
                ConfiguredLocation.read(document.get("spawn")));
        assertEquals(
                "minecraft:arena", ConfiguredLocation.read("minecraft:arena:1:2:3:0:0").world());
    }

    @Test
    void rejectsInvalidCoordinatesInsteadOfSilentlyUsingZero() throws Exception {
        var document = new YamlConfiguration();
        document.loadFromString("spawn: {world: arena, x: typo, y: 2, z: 3}\n");
        assertThrows(
                IllegalArgumentException.class,
                () -> ConfiguredLocation.read(document.get("spawn")));
        for (String value :
                new String[] {
                    "arena:1:2", "arena:NaN:2:3:0:0", "arena:1:2:3:Infinity:0", ":1:2:3:0:0"
                }) {
            assertThrows(IllegalArgumentException.class, () -> ConfiguredLocation.read(value));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> ConfiguredLocation.read(new Location(null, 1, 2, 3)));
    }
}

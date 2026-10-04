package ink.ziip.championshipscore.api.game.buildmart.config;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

class BuildMartJumpPadConfigTest {
    @Test
    void currentJumpPadCoordinatesLoadAndSaveUnderTheConfiguredName() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world-name", "buildmart_area");
        yaml.set(
                "jump-pads",
                List.of(
                        Map.of(
                                "pos1",
                                Map.of("x", 182, "y", 101, "z", 192),
                                "pos2",
                                Map.of("x", 185, "y", 101, "z", 195))));
        yaml.set("custom", "preserved");
        BuildMartConfig config = config(yaml);
        config.loadCustomFileOptions();
        assertEquals(1, config.getJumpPads().size());
        assertEquals(new Vector(182, 101, 192), config.getJumpPads().getFirst().pos1());
        assertEquals(new Vector(185, 101, 195), config.getJumpPads().getFirst().pos2());
        config.saveCustomOptions();
        assertEquals(1, yaml.getMapList("jump-pads").size());
        assertEquals("preserved", yaml.getString("custom"));
        config.loadCustomFileOptions();
        World world =
                (World)
                        Proxy.newProxyInstance(
                                World.class.getClassLoader(),
                                new Class<?>[] {World.class},
                                (proxy, method, args) ->
                                        method.getName().equals("getName")
                                                ? "buildmart_area"
                                                : null);
        assertTrue(config.isInPlayableArea(new Location(world, 189, 170, 193)));
        assertTrue(config.isInPlayableArea(new Location(world, 183, 180, 193)));
    }

    @Test
    void explicitlyEmptyNewJumpPadListDoesNotRestoreOldSelections() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("jump-pads", List.of());
        BuildMartConfig config = config(yaml);
        config.loadCustomFileOptions();
        assertTrue(config.getJumpPads().isEmpty());
    }

    private static BuildMartConfig config(YamlConfiguration yaml) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        BuildMartConfig config =
                (BuildMartConfig)
                        ((sun.misc.Unsafe) field.get(null)).allocateInstance(BuildMartConfig.class);
        var configuration = BaseConfigurationFile.class.getDeclaredField("configuration");
        configuration.setAccessible(true);
        configuration.set(config, yaml);
        return config;
    }
}

package ink.ziip.championshipscore.configuration.config;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.ConfigOption;
import ink.ziip.championshipscore.configuration.location.LocationConfig;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigurationLoadingTest {
    @BeforeEach
    void resetGlobals() {
        Globals.note = null;
        Globals.spawn = null;
    }

    @Test
    void removingAnOptionalScalarClearsItsPreviousValue() throws Exception {
        var config = new Globals(plugin());
        var yaml = new YamlConfiguration();
        yaml.set("note", "first");
        config.loadFromConfiguration(yaml);
        assertEquals("first", Globals.note);
        yaml.set("note", null);
        config.loadFromConfiguration(yaml);
        assertNull(Globals.note);
    }

    @Test
    void removingAnOptionalSpawnDoesNotKeepThePreviousTeleportTarget() throws Exception {
        var config = new Globals(plugin());
        var yaml = new YamlConfiguration();
        LocationConfig.write(yaml, "spawn", new Location(null, 10, 20, 30));
        config.loadFromConfiguration(yaml);
        assertEquals(20D, Globals.spawn.getY());
        yaml.set("spawn", null);
        config.loadFromConfiguration(yaml);
        assertNull(Globals.spawn);
    }

    @Test
    void missingOverridesUseTheBundledUtf8Default() throws Exception {
        var config = new Globals(plugin());
        config.loadDefaultOptions();
        assertEquals("模板说明", Globals.note);
        var yaml = new YamlConfiguration();
        yaml.set("note", "custom");
        config.loadFromConfiguration(yaml);
        yaml.set("note", null);
        config.loadFromConfiguration(yaml);
        assertEquals("模板说明", Globals.note);
    }

    @Test
    void privateInstanceAndInheritedGlobalOptionsShareTheLoader() throws Exception {
        var config = new InstanceOptions(plugin());
        var yaml = new YamlConfiguration();
        yaml.set("seconds", 12);
        yaml.set("note", "inherited");
        config.loadFromConfiguration(yaml);
        assertEquals(12, config.seconds);
        assertEquals("inherited", Globals.note);
        yaml.set("seconds", null);
        config.loadFromConfiguration(yaml);
        assertEquals(5, config.seconds);
    }

    @Test
    void invalidFieldDoesNotPublishOtherFieldsFromTheSameDocument() throws Exception {
        var config = new InstanceOptions(plugin());
        var yaml = new YamlConfiguration();
        yaml.set("note", "first");
        yaml.set("seconds", 12);
        config.loadFromConfiguration(yaml);
        yaml.set("note", "second");
        yaml.set("seconds", "invalid");
        var error =
                assertThrows(
                        IllegalArgumentException.class, () -> config.loadFromConfiguration(yaml));
        assertTrue(error.getMessage().contains("seconds"));
        assertEquals("first", Globals.note);
        assertEquals(12, config.seconds);
    }

    public static class Globals extends BaseConfigurationFile {
        @ConfigOption(path = "note", nullable = true)
        public static String note;

        @ConfigOption(path = "spawn", nullable = true)
        public static Location spawn;

        Globals(ChampionshipsCore plugin) {
            super(plugin);
        }

        @Override
        public String getFileName() {
            return "loading-test.yml";
        }

        @Override
        public String getResourceName() {
            return "loading-test.yml";
        }

        @Override
        public int getLatestVersion() {
            return 1;
        }
    }

    private static final class InstanceOptions extends Globals {
        @ConfigOption(path = "seconds")
        private int seconds = 5;

        private InstanceOptions(ChampionshipsCore plugin) {
            super(plugin);
        }
    }

    private static ChampionshipsCore plugin() throws Exception {
        var plugin = allocate(ChampionshipsCore.class);
        var loader = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("classLoader");
        loader.setAccessible(true);
        loader.set(plugin, ConfigurationLoadingTest.class.getClassLoader());
        return plugin;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }
}

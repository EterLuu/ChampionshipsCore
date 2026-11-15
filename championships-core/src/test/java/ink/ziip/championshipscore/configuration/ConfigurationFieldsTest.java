package ink.ziip.championshipscore.configuration;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.configuration.config.BaseConfigurationFile;
import ink.ziip.championshipscore.support.TestObjects;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

class ConfigurationFieldsTest {
    @Test
    void declaredSectionsJoinInheritedFieldsInOneSchema() {
        assertEquals(
                List.of("name", "text", "count"),
                ConfigurationFields.of(Config.class).stream()
                        .map(f -> f.getAnnotation(ConfigOption.class).path())
                        .toList());
    }

    @Test
    void duplicatePathAcrossSectionsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ConfigurationFields.of(Duplicate.class));
    }

    @Test
    void mutableInstanceFieldsAndFinalFieldsCannotMasqueradeAsStaticSections() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ConfigurationFields.of(InvalidInstance.class));
        assertThrows(
                IllegalArgumentException.class, () -> ConfigurationFields.of(InvalidFinal.class));
    }

    @Test
    void invalidSectionDoesNotPartiallyPublishOtherSections() throws Exception {
        Text.value = "before";
        Count.value = 3;
        Config config = new Config();
        var document = new YamlConfiguration();
        document.set("name", "after");
        document.set("text", "after");
        document.set("count", "not-a-number");
        assertThrows(IllegalArgumentException.class, () -> config.loadFromConfiguration(document));
        assertEquals("original", config.name);
        assertEquals("before", Text.value);
        assertEquals(3, Count.value);
    }

    @Test
    void explicitEmptyValuesAndRemovalFollowTheExistingFallbackContract() throws Exception {
        Text.value = "default";
        Count.value = 3;
        Config config = new Config();
        var document = new YamlConfiguration();
        document.set("name", "changed");
        document.set("text", "");
        document.set("count", 4);
        config.loadFromConfiguration(document);
        assertEquals("", Text.value);
        assertEquals(4, Count.value);
        config.loadFromConfiguration(new YamlConfiguration());
        assertEquals("default", Text.value);
        assertEquals(3, Count.value);
        assertEquals("original", config.name);
    }

    @Test
    void savePersistsSectionFieldsWithoutDroppingUnmappedCustomKeys(@TempDir Path folder)
            throws Exception {
        Text.value = "custom";
        Count.value = 7;
        Config config = new Config();
        config.document(folder.resolve("config.yml"));
        config.saveOptions();
        var saved = YamlConfiguration.loadConfiguration(folder.resolve("config.yml").toFile());
        assertEquals("custom", saved.getString("text"));
        assertEquals(7, saved.getInt("count"));
        assertEquals("kept", saved.getString("unmapped"));
    }

    static class Text {
        @ConfigOption(path = "text")
        public static String value = "default";
    }

    static class Count {
        @ConfigOption(path = "count")
        public static int value = 3;
    }

    static class SamePath {
        @ConfigOption(path = "text")
        public static String duplicate;
    }

    static class Instance {
        @ConfigOption(path = "instance")
        public String value;
    }

    static class Constant {
        @ConfigOption(path = "constant")
        public static final String value = "value";
    }

    @ConfigSections({Text.class, SamePath.class})
    static class Duplicate {}

    @ConfigSections({Instance.class})
    static class InvalidInstance {}

    @ConfigSections({Constant.class})
    static class InvalidFinal {}

    @ConfigSections({Text.class, Count.class})
    static class Config extends BaseConfigurationFile {
        @ConfigOption(path = "name")
        String name = "original";

        Config() throws Exception {
            super(TestObjects.allocate(ChampionshipsCore.class));
        }

        void document(Path path) {
            configuration = new YamlConfiguration();
            configuration.set("unmapped", "kept");
            configurationPath = path;
        }

        @Override
        public String getFileName() {
            return "config.yml";
        }

        @Override
        public String getResourceName() {
            return "config.yml";
        }

        @Override
        public int getLatestVersion() {
            return 1;
        }
    }
}

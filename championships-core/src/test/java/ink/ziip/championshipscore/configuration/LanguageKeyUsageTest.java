package ink.ziip.championshipscore.configuration;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;
import ink.ziip.championshipscore.configuration.config.message.ScheduleMessageConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageKeyUsageTest {
    @Test
    void messageAndScheduleKeysHaveLiveCodeReferences() throws IOException {
        assertStaticConfigUsage(MessageConfig.class, "message.yml");
        assertStaticConfigUsage(ScheduleMessageConfig.class, "schedule-message.yml");
    }

    @Test
    void bingoLocalesOnlyContainLiveKeys() throws IOException {
        String sources = javaSources().stream()
                .map(LanguageKeyUsageTest::read)
                .reduce("", (left, right) -> left + "\n" + right);
        for (String locale : List.of("zh_CN", "en_US")) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    Path.of("src/main/resources/bingo/lang/" + locale + ".yml").toFile());
            List<String> unused = leafKeys(yaml).stream()
                    .filter(key -> !key.equals("dont-edit-this.version"))
                    .filter(key -> !key.startsWith("task.family."))
                    .filter(key -> !sources.contains("\"" + key + "\""))
                    .toList();
            assertTrue(unused.isEmpty(), locale + " contains unused keys: " + unused);
        }
    }

    private static void assertStaticConfigUsage(Class<?> configClass, String resource) throws IOException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(Path.of("src/main/resources", resource).toFile());
        Set<String> configured = new HashSet<>(leafKeys(yaml));
        configured.remove("dont-edit-this.version");

        Set<String> declared = new HashSet<>();
        List<String> sources = javaSources().stream().map(LanguageKeyUsageTest::read).toList();
        for (Field field : configClass.getFields()) {
            ConfigOption option = field.getAnnotation(ConfigOption.class);
            if (option == null) continue;
            declared.add(option.path());
            String reference = configClass.getSimpleName() + "." + field.getName();
            assertTrue(sources.stream().anyMatch(source -> source.contains(reference)),
                    () -> "Unused language field: " + reference + " (" + option.path() + ")");
        }
        assertEquals(declared, configured, resource + " must contain exactly the declared language keys");
    }

    private static List<Path> javaSources() throws IOException {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<String> leafKeys(YamlConfiguration yaml) {
        return yaml.getKeys(true).stream()
                .filter(key -> !yaml.isConfigurationSection(key))
                .sorted()
                .toList();
    }
}

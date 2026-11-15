package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

class WorkerConfigTest {
    @Test
    void testOptInDefaultsOffAndOnlyExplicitBooleanTrueEnablesTests() throws Exception {
        YamlConfiguration config;
        try (var input = WorkerConfigTest.class.getResourceAsStream("/config.yml")) {
            config =
                    YamlConfiguration.loadConfiguration(
                            new InputStreamReader(
                                    Objects.requireNonNull(input), StandardCharsets.UTF_8));
        }
        config.set("enabled", true);
        assertFalse(WorkerConfig.load(config).testEnabled());
        config.set("test", null);
        assertFalse(WorkerConfig.load(config).testEnabled());
        config.set("test.enabled", "true");
        assertFalse(WorkerConfig.load(config).testEnabled());
        config.set("test.enabled", true);
        assertTrue(WorkerConfig.load(config).testEnabled());
    }
}

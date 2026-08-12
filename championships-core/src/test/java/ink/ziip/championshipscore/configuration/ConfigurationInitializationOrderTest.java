package ink.ziip.championshipscore.configuration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationInitializationOrderTest {
    @Test
    void upgradeRunsBeforeRuntimeBinding() throws Exception {
        String source = Files.readString(Path.of("src/main/java/ink/ziip/championshipscore/configuration/config/"
                + "BaseConfigurationFile.java"));
        int initializer = source.indexOf("public void initializeConfiguration(Path pluginFolder, boolean autoUpgrade)");
        int upgrade = source.indexOf("checkVersion(autoUpgrade);", initializer);
        int binding = source.indexOf("loadFileOptions();", initializer);

        assertTrue(initializer >= 0 && upgrade > initializer && binding > upgrade,
                "Disk configuration must be upgraded before runtime fields are loaded and validated");
    }
}

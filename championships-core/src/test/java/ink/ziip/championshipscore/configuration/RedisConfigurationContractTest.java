package ink.ziip.championshipscore.configuration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisConfigurationContractTest {
    @Test
    void redisIsSharedInfrastructureRatherThanBingoOwnedConfiguration() throws Exception {
        String config = Files.readString(Path.of("src/main/resources/config.yml"));
        assertTrue(config.contains("version: 16"));
        assertTrue(config.contains("\nredis:\n"));
        assertTrue(config.contains("  instance-id:"));
        assertTrue(config.contains("  reconciliation-seconds:"));
        assertTrue(!config.contains("  redis:\n    uri:"));
    }

    @Test
    void remoteBingoRowsCarryCoreOwnership() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/database/schema.sql"));
        assertTrue(schema.contains("`ownerInstance` VARCHAR(128) NOT NULL"));
    }
}

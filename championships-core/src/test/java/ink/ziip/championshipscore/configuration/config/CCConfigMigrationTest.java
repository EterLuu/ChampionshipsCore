package ink.ziip.championshipscore.configuration.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CCConfigMigrationTest {
    @Test
    void firstUpgradeMovesRemoteBingoRedisBeforeRuntimeBinding() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        old.loadFromString("""
                dont-edit-this:
                  version: 15
                bingo:
                  execution-mode: REMOTE
                  redis:
                    uri: redis://minecraft-redis:6379/0
                    namespace: championships
                    consumer-group: championships-core
                    stream-max-length: 100000
                    block-timeout-ms: 2000
                    reclaim-idle-ms: 15000
                    max-deliveries: 8
                """);

        CCConfig.migrateLegacyRedisConfiguration(old);

        assertTrue(old.getBoolean("redis.enabled"));
        assertEquals("auto", old.getString("redis.instance-id"));
        assertEquals("redis://minecraft-redis:6379/0", old.getString("redis.uri"));
        assertEquals("championships", old.getString("redis.namespace"));
        assertEquals("championships-core", old.getString("redis.consumer-group-prefix"));
        assertEquals(30L, old.getLong("redis.reconciliation-seconds"));
        assertFalse(old.contains("bingo.redis"));
    }
}

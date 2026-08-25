package ink.ziip.championshipscore.configuration.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CCConfigMigrationTest {
    @Test
    void replacesLegacyUuidModesWithOfflineOrProfileUuidLookup() throws Exception {
        YamlConfiguration offline = new YamlConfiguration();
        offline.loadFromString("""
                identity:
                  server-uuid-source: OFFLINE
                  server-profile-api-base-url: https://api.mojang.com
                  custom-profile-api-base-url: https://unused.example.test/api/yggdrasil
                """);
        CCConfig.migrateIdentityConfiguration(offline);
        assertEquals("OFFLINE", offline.getString("identity.mode"));
        assertEquals("https://api.mojang.com", offline.getString("identity.profile-api-base-url"));
        assertFalse(offline.contains("identity.server-uuid-source"));
        assertFalse(offline.contains("identity.custom-profile-api-base-url"));

        YamlConfiguration online = new YamlConfiguration();
        online.loadFromString("""
                identity:
                  server-uuid-source: PROFILE_API
                  server-profile-api-base-url: https://auth.example.test/api/yggdrasil
                """);
        CCConfig.migrateIdentityConfiguration(online);
        assertEquals("PROFILE_UUID", online.getString("identity.mode"));
        assertEquals("https://auth.example.test/api/yggdrasil",
                online.getString("identity.profile-api-base-url"));

        YamlConfiguration custom = new YamlConfiguration();
        custom.loadFromString("""
                identity:
                  mode: CUSTOM_UUID
                  server-uuid-source: OFFLINE
                  server-profile-api-base-url: https://api.mojang.com
                  custom-profile-api-base-url: https://web.example.test/api/yggdrasil
                """);
        CCConfig.migrateIdentityConfiguration(custom);
        assertEquals("OFFLINE", custom.getString("identity.mode"));
        assertEquals("https://api.mojang.com",
                custom.getString("identity.profile-api-base-url"));

        YamlConfiguration oldOnline = new YamlConfiguration();
        oldOnline.loadFromString("""
                identity:
                  mode: ONLINE
                  profile-api-base-url: https://profiles.example.test
                """);
        CCConfig.migrateIdentityConfiguration(oldOnline);
        assertEquals("PROFILE_UUID", oldOnline.getString("identity.mode"));
    }

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

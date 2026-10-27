package ink.ziip.championshipscore.authproxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ink.ziip.championshipscore.auth.BanPresentation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class ChampionshipsAuthProxyPluginTest {
    @TempDir Path tempDirectory;

    @Test
    void olderConfigurationInheritsNewDefaultsAndPreservesExplicitMessages() throws Exception {
        Path file = tempDirectory.resolve("config.yml");
        String original = "messages:\n  banned: '&c%reason%/%expires%'\n  not-bound: ''\n";
        Files.writeString(file, original);
        try (var defaults = getClass().getResourceAsStream("/config.yml")) {
            var config = ChampionshipsAuthProxyPlugin.loadConfiguration(file.toFile(), defaults);
            assertEquals("&c%reason%/%expires%", config.getString("messages.banned"));
            assertEquals("", config.getString("messages.not-bound"));
            var presentation = BanPresentation.from(config::getString, "messages.banned");
            assertEquals("&c违反服务器规则/请查看账号页面", presentation.render(null, null));
            assertTrue(config.getString("messages.unavailable").contains("账号服务"));
            assertEquals(original, Files.readString(file));
        }
    }

    @Test
    void migratesLegacyStateFileWithoutLosingContent() throws Exception {
        Path legacy = tempDirectory.resolve("ban-state.properties");
        Path current = tempDirectory.resolve("state.properties");
        Files.writeString(legacy, "ban-event-cursor=42\nmaintenance=false\n");

        assertTrue(ChampionshipsAuthProxyPlugin.migrateLegacyStateFile(tempDirectory.toFile()));

        assertFalse(Files.exists(legacy));
        assertEquals("ban-event-cursor=42\nmaintenance=false\n", Files.readString(current));
    }

    @Test
    void neverOverwritesExistingStateFileWithLegacyState() throws Exception {
        Path legacy = tempDirectory.resolve("ban-state.properties");
        Path current = tempDirectory.resolve("state.properties");
        Files.writeString(legacy, "ban-event-cursor=old\n");
        Files.writeString(current, "ban-event-cursor=current\n");

        assertFalse(ChampionshipsAuthProxyPlugin.migrateLegacyStateFile(tempDirectory.toFile()));

        assertEquals("ban-event-cursor=current\n", Files.readString(current));
        assertEquals("ban-event-cursor=old\n", Files.readString(legacy));
    }

    @Test
    void rejectionLogsRetainFieldsAndNormalizeMissingOrUnsafeText() {
        String[][] examples = {
            {
                "BlockedPlayer",
                "BANNED",
                "Repeated cheating",
                "Rejected login: player=BlockedPlayer, status=BANNED, reason=Repeated cheating"
            },
            {null, " ", "", "Rejected login: player=unknown, status=UNKNOWN, reason=not provided"},
            {
                "Player\nForgedEntry",
                "UNBOUND\r\nWARNING",
                "line one\tline two\u0000tail",
                "Rejected login: player=Player ForgedEntry, status=UNBOUND WARNING, reason=line one"
                        + " line two tail"
            }
        };
        for (String[] example : examples) {
            assertEquals(
                    example[3],
                    ChampionshipsAuthProxyPlugin.rejectionLog(example[0], example[1], example[2]),
                    "player=" + example[0]);
        }
    }
}

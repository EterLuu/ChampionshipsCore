package ink.ziip.championshipscore.authproxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class ChampionshipsAuthProxyPluginTest {
    @TempDir Path tempDirectory;

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

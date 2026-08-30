package ink.ziip.championshipscore.authproxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChampionshipsAuthProxyPluginTest {
    @TempDir
    Path tempDirectory;

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
    void rejectionLogContainsPlayerStatusAndReason() {
        String log = ChampionshipsAuthProxyPlugin.rejectionLog("BlockedPlayer", "BANNED", "Repeated cheating");

        assertTrue(log.contains("player=BlockedPlayer"));
        assertTrue(log.contains("status=BANNED"));
        assertTrue(log.contains("reason=Repeated cheating"));
    }

    @Test
    void rejectionLogUsesFallbacksForMissingValues() {
        assertEquals("Rejected login: player=unknown, status=UNKNOWN, reason=not provided",
                ChampionshipsAuthProxyPlugin.rejectionLog(null, " ", ""));
    }

    @Test
    void rejectionLogPreventsMultilineLogInjection() {
        String log = ChampionshipsAuthProxyPlugin.rejectionLog(
                "Player\nForgedEntry", "UNBOUND\r\nWARNING", "line one\tline two\u0000tail");

        assertFalse(log.contains("\n"));
        assertFalse(log.contains("\r"));
        assertFalse(log.contains("\t"));
        assertFalse(log.contains("\u0000"));
        assertTrue(log.contains("player=Player ForgedEntry"));
        assertTrue(log.contains("status=UNBOUND WARNING"));
        assertTrue(log.contains("reason=line one line two tail"));
    }
}

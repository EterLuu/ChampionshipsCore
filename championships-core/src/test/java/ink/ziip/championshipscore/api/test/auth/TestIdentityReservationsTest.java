package ink.ziip.championshipscore.api.test.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

class TestIdentityReservationsTest {
    @TempDir Path directory;

    @Test
    void restartRetainsEveryHistoricalNameAndUuidWithoutRestoringAdmission() throws Exception {
        Path file = directory.resolve("test/identities.tsv");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var store = new TestIdentityReservations(file);
        store.reserve("TestBot", first);
        store.reserve("testbot", second);
        store.reserve("oldname", first);
        var restarted = new TestIdentityReservations(file);
        assertTrue(restarted.contains("TESTBOT", UUID.randomUUID()));
        assertTrue(restarted.contains("renamed", first));
        assertTrue(restarted.contains("renamed", second));
        assertTrue(restarted.containsName("oldname"));
        assertFalse(restarted.contains("ordinary", UUID.randomUUID()));
        assertEquals(4, Files.readAllLines(file).size());
        try (var files = Files.list(file.getParent())) {
            assertEquals(1, files.count());
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "CC_TEST_IDENTITIES_V2\n",
                "CC_TEST_IDENTITIES_V1\nwrong\n",
                "CC_TEST_IDENTITIES_V1\ntestbot\tnot-a-uuid\n",
                "CC_TEST_IDENTITIES_V1\nbad-name\t12345678-1234-1234-1234-123456789012\n"
            })
    void malformedStateFailsClosed(String content) throws Exception {
        Path file = directory.resolve("identities.tsv");
        Files.writeString(file, content);
        assertThrows(IllegalStateException.class, () -> new TestIdentityReservations(file));
    }

    @Test
    void failedWriteNeverBecomesAnImplicitSuccessfulCommitOnRetry() throws Exception {
        Path parent = directory.resolve("blocked");
        Files.writeString(parent, "not a directory");
        Path file = parent.resolve("identities.tsv");
        var store = new TestIdentityReservations(file);
        UUID id = UUID.randomUUID();
        assertThrows(UncheckedIOException.class, () -> store.reserve("testbot", id));
        assertTrue(store.contains("testbot", id));
        assertThrows(UncheckedIOException.class, () -> store.reserve("testbot", id));
        Files.delete(parent);
        store.reserve("testbot", id);
        assertTrue(new TestIdentityReservations(file).contains("renamed", id));
    }

    @Test
    void failedReplacementPreservesCommittedStateAndCanBeRetried() throws Exception {
        Path file = directory.resolve("identities.tsv");
        var store = new TestIdentityReservations(file);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        store.reserve("firstbot", first);
        Path saved = directory.resolve("saved.tsv");
        Files.move(file, saved);
        Files.createDirectory(file);
        assertThrows(UncheckedIOException.class, () -> store.reserve("secondbot", second));
        assertTrue(new TestIdentityReservations(saved).containsName("firstbot"));
        assertFalse(new TestIdentityReservations(saved).containsName("secondbot"));
        Files.delete(file);
        Files.move(saved, file);
        store.reserve("secondbot", second);
        var restarted = new TestIdentityReservations(file);
        assertTrue(restarted.containsName("firstbot"));
        assertTrue(restarted.containsName("secondbot"));
    }
}

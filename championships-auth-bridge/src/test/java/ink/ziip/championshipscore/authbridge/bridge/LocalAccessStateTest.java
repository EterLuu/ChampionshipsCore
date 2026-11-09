package ink.ziip.championshipscore.authbridge.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class LocalAccessStateTest {
    @Test
    void retainsOnlyAuthorizedIdentitiesAndVersions() throws Exception {
        File file = Files.createTempFile("authbridge-state", ".yml").toFile();
        try {
            LocalAccessState state = new LocalAccessState(file);
            UUID allowedUuid = UUID.randomUUID();
            UUID removedUuid = UUID.randomUUID();
            state.recordIdentity("Allowed", UUID.randomUUID().toString(), allowedUuid.toString());
            state.recordIdentity("Removed", UUID.randomUUID().toString(), removedUuid.toString());
            state.setAuthVersion("Allowed", 4);
            state.setAuthVersion("Removed", 7);

            state.retainIdentities(Set.of("allowed"));

            assertEquals(allowedUuid, state.expectedUuid("ALLOWED"));
            assertNull(state.expectedUuid("Removed"));
            assertEquals(4, state.authVersion("Allowed"));
            assertEquals(0, state.authVersion("Removed"));
            assertEquals(allowedUuid, new LocalAccessState(file).expectedUuid("Allowed"));
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    void persistsBanStateAcrossRestart() throws Exception {
        File file = Files.createTempFile("authbridge-ban-state", ".yml").toFile();
        try {
            LocalAccessState state = new LocalAccessState(file);
            state.ban("BlockedPlayer", "rule violation", "2030-01-01T00:00:00Z");

            LocalAccessState.Ban restored =
                    new LocalAccessState(file)
                            .activeBan("blockedplayer", Instant.parse("2029-01-01T00:00:00Z"));

            assertEquals("rule violation", restored.reason());
            assertEquals("2030-01-01T00:00:00Z", restored.expiresAt());
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    void persistsBotNetworkMetadataAcrossRestart() throws Exception {
        File file = Files.createTempFile("authbridge-bot-state", ".yml").toFile();
        try {
            LocalAccessState state = new LocalAccessState(file);
            state.recordIdentity(
                    "autobot00",
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    true,
                    List.of("10.20.0.0/16"));
            state.advance("1");

            LocalAccessState.Identity restored = new LocalAccessState(file).identity("autobot00");
            assertEquals(true, restored.bot());
            assertEquals(List.of("10.20.0.0/16"), restored.allowedCidrs());
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    void resynchronizationForgetsOutboxProgressButKeepsLoginsUntilTheSnapshot() throws Exception {
        File file = Files.createTempFile("authbridge-state", ".yml").toFile();
        try {
            LocalAccessState state = new LocalAccessState(file);
            UUID uuid = UUID.randomUUID();
            state.recordIdentity("Player", UUID.randomUUID().toString(), uuid.toString());
            state.setAuthVersion("Player", 9);
            state.markBindingSnapshotApplied();
            state.advance("120");

            state.beginResynchronization();

            LocalAccessState reloaded = new LocalAccessState(file);
            for (LocalAccessState current : List.of(state, reloaded)) {
                assertEquals("0", current.cursor());
                assertNull(current.pendingAcknowledgement());
                assertFalse(current.bindingSnapshotApplied());
                assertTrue(current.resynchronizing());
                // A restored website reissues lower versions; they must apply again.
                assertEquals(0, current.authVersion("Player"));
                assertEquals(uuid, current.expectedUuid("Player"));
            }

            state.finishResynchronization();
            assertFalse(new LocalAccessState(file).resynchronizing());
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    void replacesBansWithTheWebsiteList() throws Exception {
        File file = Files.createTempFile("authbridge-state", ".yml").toFile();
        try {
            LocalAccessState state = new LocalAccessState(file);
            state.ban("OnlyLocal", "stale", null);
            Instant now = Instant.now();

            state.replaceBans(
                    Map.of(
                            "Website",
                            new LocalAccessState.Ban(
                                    "cheating", now.plusSeconds(3600).toString())));

            LocalAccessState reloaded = new LocalAccessState(file);
            assertNull(reloaded.activeBan("OnlyLocal", now));
            assertNotNull(reloaded.activeBan("website", now));
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }
}

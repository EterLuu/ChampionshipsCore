package ink.ziip.championshipscore.authproxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyAccessStateTest {
    private static final String UUID = "11111111-1111-4111-8111-111111111111";

    @TempDir
    Path tempDirectory;

    @Test
    void persistsProfilesBansMaintenanceAndCursorAcrossRestart() throws Exception {
        Path file = tempDirectory.resolve("access.properties");
        ProxyAccessState state = new ProxyAccessState(file.toFile());
        ProxyIdentityClient.ProxyBanSnapshot snapshot = new ProxyIdentityClient.ProxyBanSnapshot();
        snapshot.maintenance = false;
        snapshot.nextCursor = "9";
        snapshot.profiles = List.of(profile("AllowedPlayer", "ALLOWED", UUID));
        snapshot.bans = List.of(ban("BannedPlayer", "rule violation", null));

        state.replaceSnapshot(snapshot, Instant.parse("2026-08-30T00:00:00Z"));
        ProxyAccessState restored = new ProxyAccessState(file.toFile());

        assertEquals("9", restored.cursor());
        assertTrue(restored.initialized());
        assertEquals(UUID, restored.cachedProfile("allowedplayer", Duration.ZERO, Instant.now()).uuid);
        assertEquals("BANNED", restored.cachedProfile("BannedPlayer", Duration.ZERO, Instant.now()).status);
        assertEquals("rule violation", restored.cachedProfile("BannedPlayer", Duration.ZERO, Instant.now()).reason);
    }

    @Test
    void legacyBanCursorStillRequiresOneFullAccessSnapshot() throws Exception {
        Path file = tempDirectory.resolve("legacy.properties");
        java.nio.file.Files.writeString(file, "ban-event-cursor=79\n");

        ProxyAccessState state = new ProxyAccessState(file.toFile());

        assertEquals("79", state.cursor());
        assertFalse(state.initialized());
    }

    @Test
    void appliesRenameRevocationAndMaintenanceFromIncrementalState() throws Exception {
        ProxyAccessState state = new ProxyAccessState(tempDirectory.resolve("changes.properties").toFile());
        ProxyIdentityClient.ProxyBanSnapshot snapshot = new ProxyIdentityClient.ProxyBanSnapshot();
        snapshot.maintenance = false;
        snapshot.nextCursor = "2";
        snapshot.profiles = List.of(profile("OldName", "ALLOWED", UUID));
        snapshot.bans = List.of();
        state.replaceSnapshot(snapshot, Instant.now());

        ProxyIdentityClient.ProxyChange rename = new ProxyIdentityClient.ProxyChange();
        rename.operation = "USERNAME_UPDATED";
        rename.authmeUsername = "NewName";
        rename.previousUsername = "OldName";
        rename.status = "ALLOWED";
        rename.uuid = UUID;
        ProxyIdentityClient.ProxyChangeBatch batch = new ProxyIdentityClient.ProxyChangeBatch();
        batch.maintenance = false;
        batch.nextCursor = "3";
        batch.changes = List.of(rename);
        state.applyChanges(batch, Instant.now());

        assertNull(state.cachedProfile("OldName", Duration.ZERO, Instant.now()));
        assertEquals(UUID, state.cachedProfile("NewName", Duration.ZERO, Instant.now()).uuid);

        batch.maintenance = true;
        batch.nextCursor = "4";
        batch.changes = List.of();
        state.applyChanges(batch, Instant.now());
        assertEquals("MAINTENANCE", state.cachedProfile("NewName", Duration.ZERO, Instant.now()).status);
    }

    @Test
    void optionalMaximumAgeRejectsStaleProfiles() throws Exception {
        ProxyAccessState state = new ProxyAccessState(tempDirectory.resolve("stale.properties").toFile());
        ProxyIdentityClient.LoginProfile live = profile(null, "ALLOWED", UUID);
        Instant recordedAt = Instant.parse("2026-08-30T00:00:00Z");
        state.recordLiveProfile("Player", live, recordedAt);

        assertEquals(UUID, state.cachedProfile("Player", Duration.ZERO, recordedAt.plus(Duration.ofDays(30))).uuid);
        assertNull(state.cachedProfile("Player", Duration.ofHours(1), recordedAt.plus(Duration.ofHours(2))));
    }

    private static ProxyIdentityClient.LoginProfile profile(String username, String status, String uuid) {
        ProxyIdentityClient.LoginProfile profile = new ProxyIdentityClient.LoginProfile();
        profile.username = username;
        profile.status = status;
        profile.uuid = uuid;
        return profile;
    }

    private static ProxyIdentityClient.ProxyBan ban(String username, String reason, String expiresAt) {
        ProxyIdentityClient.ProxyBan ban = new ProxyIdentityClient.ProxyBan();
        ban.username = username;
        ban.reason = reason;
        ban.expiresAt = expiresAt;
        return ban;
    }
}

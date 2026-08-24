package ink.ziip.championshipscore.authbridge;

import ink.ziip.championshipscore.authbridge.bridge.LocalAccessState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessListenerTest {
    private static final String NOT_WHITELISTED = "not whitelisted";
    private static final String BANNED = "banned";
    private static final String MAINTENANCE = "maintenance";
    private static final String UNAVAILABLE = "unavailable";
    private static final String UUID_MISMATCH = "uuid mismatch";
    private static final String PLAYER_UUID = "f84c6a79-34ab-4d3e-b64a-9b8c8d7b42fb";

    @TempDir
    Path tempDirectory;

    @Test
    void rejectsNonWhitelistedPlayerDuringPreLoginAfterSynchronization() throws Exception {
        LocalAccessState state = synchronizedState();
        AccessListener listener = listener(state, true);

        AccessListener.AccessDecision decision = listener.accessDecision("PendingPlayer");

        assertEquals(AccessListener.AccessResult.NOT_WHITELISTED, decision.result());
        assertEquals(NOT_WHITELISTED, decision.message());
    }

    @Test
    void rejectsBannedPlayerDuringPreLoginBeforeWhitelistCheck() throws Exception {
        LocalAccessState state = synchronizedState();
        state.whitelist("BannedPlayer", PLAYER_UUID);
        state.ban("BannedPlayer", "testing reason", null);
        AccessListener listener = listener(state, true);

        AccessListener.AccessDecision decision = listener.accessDecision("BannedPlayer");

        assertEquals(AccessListener.AccessResult.BANNED, decision.result());
        assertEquals(BANNED + "\ntesting reason", decision.message());
    }

    @Test
    void allowsWhitelistedPlayerDuringPreLogin() throws Exception {
        LocalAccessState state = synchronizedState();
        state.whitelist("ApprovedPlayer", PLAYER_UUID);

        AccessListener.AccessDecision decision = listener(state, true).accessDecision("approvedplayer");

        assertEquals(AccessListener.AccessResult.ALLOWED, decision.result());
    }

    @Test
    void reportsBridgeUnavailableBeforeFirstSynchronization() {
        AccessListener listener = listener(new LocalAccessState(tempDirectory.resolve("closed.yml").toFile()), true);

        AccessListener.AccessDecision decision = listener.accessDecision("PendingPlayer");

        assertEquals(AccessListener.AccessResult.BRIDGE_UNAVAILABLE, decision.result());
        assertEquals(UNAVAILABLE, decision.message());
    }

    @Test
    void failOpenAllowsBeforeFirstSynchronization() {
        AccessListener listener = listener(new LocalAccessState(tempDirectory.resolve("open.yml").toFile()), false);

        AccessListener.AccessDecision decision = listener.accessDecision("PendingPlayer");

        assertEquals(AccessListener.AccessResult.ALLOWED, decision.result());
    }

    @Test
    void banStillAppliesBeforeFirstSynchronizationInFailOpenMode() {
        LocalAccessState state = new LocalAccessState(tempDirectory.resolve("banned-open.yml").toFile());
        state.ban("BlockedPlayer", "bridge reason", null);
        AccessListener listener = listener(state, false);

        AccessListener.AccessDecision decision = listener.accessDecision("BlockedPlayer");

        assertEquals(AccessListener.AccessResult.BANNED, decision.result());
        assertEquals(BANNED + "\nbridge reason", decision.message());
    }

    @Test
    void expiredBanFallsBackToWhitelistDecision() throws Exception {
        LocalAccessState state = synchronizedState();
        state.ban("FormerlyBlocked", "expired", "2000-01-01T00:00:00Z");
        state.whitelist("FormerlyBlocked", PLAYER_UUID);

        AccessListener.AccessDecision decision = listener(state, true).accessDecision("FormerlyBlocked");

        assertEquals(AccessListener.AccessResult.ALLOWED, decision.result());
    }

    @Test
    void rejectsEveryoneWhileMaintenanceIsActive() throws Exception {
        LocalAccessState state = synchronizedState();
        state.whitelist("ApprovedPlayer", PLAYER_UUID);
        state.beginMaintenance("13aa576f-fcb9-445c-9886-f830059d810c");

        AccessListener.AccessDecision decision = listener(state, true).accessDecision("ApprovedPlayer");

        assertEquals(AccessListener.AccessResult.MAINTENANCE, decision.result());
        assertEquals(MAINTENANCE, decision.message());
    }

    @Test
    void persistsExpectedUuidAndMaintenanceAcrossRestart() throws Exception {
        Path statePath = tempDirectory.resolve("identity-state.yml");
        LocalAccessState state = new LocalAccessState(statePath.toFile());
        state.whitelist("ApprovedPlayer", PLAYER_UUID);
        state.beginMaintenance("13aa576f-fcb9-445c-9886-f830059d810c");

        LocalAccessState restored = new LocalAccessState(statePath.toFile());

        assertEquals(UUID.fromString(PLAYER_UUID), restored.expectedUuid("approvedplayer"));
        assertTrue(restored.maintenanceInProgress());
    }

    @Test
    void verifiesTheEffectiveGameUuidInsteadOfTheWebsiteAccountId() throws Exception {
        Path statePath = tempDirectory.resolve("effective-uuid-state.yml");
        LocalAccessState state = new LocalAccessState(statePath.toFile());
        String websiteAccountId = "11111111-1111-4111-8111-111111111111";
        state.whitelist("ApprovedPlayer", websiteAccountId, PLAYER_UUID);
        state.advance("1");

        LocalAccessState restored = new LocalAccessState(statePath.toFile());
        assertEquals(UUID.fromString(PLAYER_UUID), restored.expectedUuid("ApprovedPlayer"));
        assertEquals(AccessListener.AccessResult.UUID_MISMATCH,
                listener(restored, true).accessDecision("ApprovedPlayer", UUID.fromString(websiteAccountId)).result());
        assertEquals(AccessListener.AccessResult.ALLOWED,
                listener(restored, true).accessDecision("ApprovedPlayer", UUID.fromString(PLAYER_UUID)).result());
    }

    @Test
    void rejectsUuidThatDoesNotMatchAuthlibIdentity() throws Exception {
        LocalAccessState state = synchronizedState();
        state.whitelist("ApprovedPlayer", PLAYER_UUID);

        AccessListener.AccessDecision decision = listener(state, true).accessDecision(
                "ApprovedPlayer", UUID.fromString("11111111-1111-4111-8111-111111111111"));

        assertEquals(AccessListener.AccessResult.UUID_MISMATCH, decision.result());
        assertEquals(UUID_MISMATCH, decision.message());
    }

    @Test
    void persistsPendingAcknowledgementUntilWebAcceptsIt() throws Exception {
        Path statePath = tempDirectory.resolve("pending-ack-state.yml");
        LocalAccessState state = new LocalAccessState(statePath.toFile());
        state.advance("9");

        LocalAccessState restored = new LocalAccessState(statePath.toFile());
        assertEquals("9", restored.pendingAcknowledgement().cursor());

        restored.confirmAcknowledged("9");
        assertNull(new LocalAccessState(statePath.toFile()).pendingAcknowledgement());
    }

    private LocalAccessState synchronizedState() throws Exception {
        LocalAccessState state = new LocalAccessState(tempDirectory.resolve(UUID.randomUUID() + ".yml").toFile());
        state.advance("1");
        return state;
    }

    private static AccessListener listener(LocalAccessState state, boolean failClosed) {
        return new AccessListener(state, failClosed, NOT_WHITELISTED, BANNED, MAINTENANCE, UNAVAILABLE, UUID_MISMATCH);
    }
}

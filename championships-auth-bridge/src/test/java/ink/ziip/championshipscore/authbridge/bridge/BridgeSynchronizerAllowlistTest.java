package ink.ziip.championshipscore.authbridge.bridge;

import ink.ziip.championshipscore.authbridge.model.BridgeControlPlayer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BridgeSynchronizerAllowlistTest {
    private BridgeSynchronizer synchronizer() {
        return new BridgeSynchronizer(null, null, null, null, "", "",
                new BridgeUuidResolver(Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    @Test
    void buildsExplicitUsernameAndUuidAllowlist() {
        UUID uuidA = UUID.randomUUID();
        UUID uuidB = UUID.randomUUID();
        BridgeControlPlayer first = new BridgeControlPlayer(UUID.randomUUID().toString(), "Notch",
                null, "UUID", uuidA.toString(), null, null);
        BridgeControlPlayer second = new BridgeControlPlayer(UUID.randomUUID().toString(), "EterLzb",
                null, "UUID", uuidB.toString(), null, null);

        BridgeSynchronizer.RemoveUnknownAllowlist allowlist =
                synchronizer().parseRemoveUnknownAllowlist(List.of(first, second));

        assertEquals(Set.of("notch", "eterlzb"), allowlist.usernames());
        assertEquals(Set.of(uuidA, uuidB), allowlist.uuids());
    }

    @Test
    void rejectsDuplicateNamesAccountsOrUuidsBeforeChangingData() {
        BridgeControlPlayer first = new BridgeControlPlayer(UUID.randomUUID().toString(), "Notch",
                null, "UUID", UUID.randomUUID().toString(), null, null);
        BridgeControlPlayer duplicateName = new BridgeControlPlayer(UUID.randomUUID().toString(), "notch",
                null, "UUID", UUID.randomUUID().toString(), null, null);

        assertThrows(IllegalArgumentException.class,
                () -> synchronizer().parseRemoveUnknownAllowlist(List.of(first, duplicateName)));
        assertThrows(IllegalArgumentException.class,
                () -> synchronizer().parseRemoveUnknownAllowlist(List.of(first, first)));
        assertThrows(IllegalArgumentException.class,
                () -> synchronizer().parseRemoveUnknownAllowlist(List.of(
                        first,
                        new BridgeControlPlayer(UUID.randomUUID().toString(), "Other",
                                null, "UUID", first.minecraftUuid(), null, null))));
    }
}

package ink.ziip.championshipscore.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthIdentityTest {
    @Test
    void normalizesValidMinecraftUsernames() {
        assertEquals("player_one", AuthIdentity.normalizeUsername("Player_One"));
        assertThrows(IllegalArgumentException.class, () -> AuthIdentity.normalizeUsername("bad name"));
    }

    @Test
    void parsesDashedAndCompactUuids() {
        UUID expected = UUID.fromString("11111111-2222-4333-8444-555555555555");

        assertEquals(expected, AuthIdentity.parseUuid(expected.toString(), "uuid"));
        assertEquals(expected, AuthIdentity.parseUuid("11111111222243338444555555555555", "uuid"));
        assertThrows(IllegalArgumentException.class, () -> AuthIdentity.parseUuid("not-a-uuid", "uuid"));
    }

    @Test
    void usesTheVanillaOfflineUuidAlgorithm() {
        UUID expected = UUID.nameUUIDFromBytes("OfflinePlayer:Player_One".getBytes(StandardCharsets.UTF_8));

        assertEquals(expected, AuthIdentity.offlineUuid("Player_One"));
    }

    @Test
    void rejectsUnknownAdmissionOwners() {
        assertEquals(AuthAdmissionOwner.PROXY, AuthAdmissionOwner.parse("proxy", AuthAdmissionOwner.BRIDGE));
        assertThrows(IllegalArgumentException.class,
                () -> AuthAdmissionOwner.parse("automatic", AuthAdmissionOwner.PROXY));
    }
}

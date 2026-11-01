package ink.ziip.championshipscore.api.player;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class PlayerManagerEphemeralIdentityTest {
    @Test
    void refusesToOverwriteCachedFormalUsername() {
        PlayerManager manager = new PlayerManager(null);
        manager.cacheKnownIdentity("FormalPlayer", UUID.randomUUID());

        assertThrows(
                IllegalStateException.class,
                () -> manager.prepareEphemeralTestIdentity("formalplayer", UUID.randomUUID()));
    }

    @Test
    void refusesToReuseCachedFormalUuidForAnotherUsername() {
        PlayerManager manager = new PlayerManager(null);
        UUID formalUuid = UUID.randomUUID();
        manager.cacheKnownIdentity("FormalPlayer", formalUuid);

        assertThrows(
                IllegalStateException.class,
                () -> manager.prepareEphemeralTestIdentity("testbot", formalUuid));
    }
}

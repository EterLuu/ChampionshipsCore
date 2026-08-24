package ink.ziip.championshipscore.api.player.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerUuidSourceTest {
    @Test
    void defaultsToOfflineAndAcceptsBothSupportedModes() {
        assertEquals(PlayerUuidSource.OFFLINE, PlayerUuidSource.parse(null));
        assertEquals(PlayerUuidSource.OFFLINE, PlayerUuidSource.parse("offline"));
        assertEquals(PlayerUuidSource.ONLINE, PlayerUuidSource.parse("ONLINE"));
    }

    @Test
    void rejectsRemovedUuidModes() {
        assertThrows(IllegalArgumentException.class, () -> PlayerUuidSource.parse("SERVER_UUID"));
        assertThrows(IllegalArgumentException.class, () -> PlayerUuidSource.parse("CUSTOM_UUID"));
    }
}

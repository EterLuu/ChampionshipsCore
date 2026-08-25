package ink.ziip.championshipscore.api.player.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerUuidSourceTest {
    @Test
    void defaultsToOfflineAndAcceptsBothSupportedModes() {
        assertEquals(PlayerUuidSource.OFFLINE, PlayerUuidSource.parse(null));
        assertEquals(PlayerUuidSource.OFFLINE, PlayerUuidSource.parse("offline"));
        assertEquals(PlayerUuidSource.PROFILE_UUID, PlayerUuidSource.parse("PROFILE_UUID"));
    }

    @Test
    void rejectsRemovedUuidModes() {
        assertThrows(IllegalArgumentException.class, () -> PlayerUuidSource.parse("SERVER_UUID"));
        assertThrows(IllegalArgumentException.class, () -> PlayerUuidSource.parse("CUSTOM_UUID"));
        assertThrows(IllegalArgumentException.class, () -> PlayerUuidSource.parse("ONLINE"));
    }

    @Test
    void profileUuidRequiresAUsableProfileApiBaseUrl() {
        assertDoesNotThrow(() -> PlayerUuidSource.PROFILE_UUID
                .validateConfiguration("http://cc-web:3000/api/yggdrasil"));
        assertDoesNotThrow(() -> PlayerUuidSource.OFFLINE.validateConfiguration(null));
        assertThrows(IllegalArgumentException.class, () -> PlayerUuidSource.PROFILE_UUID.validateConfiguration(""));
        assertThrows(IllegalArgumentException.class, () -> PlayerUuidSource.PROFILE_UUID
                .validateConfiguration("ftp://profiles.example.test"));
        assertThrows(IllegalArgumentException.class, () -> PlayerUuidSource.PROFILE_UUID
                .validateConfiguration("https://profiles.example.test?unexpected=true"));
    }
}

package ink.ziip.championshipscore.api.player.dao;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerDaoImplTest {
    @Test
    void rejectsNullAllowedUuidWithoutCallingImmutableSetContains() {
        Set<UUID> allowedUuids = Collections.singleton(null);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new PlayerDaoImpl().removeUnknown(allowedUuids));

        assertEquals("Core unknown removal requires a non-null allowed UUID set", failure.getMessage());
    }
}

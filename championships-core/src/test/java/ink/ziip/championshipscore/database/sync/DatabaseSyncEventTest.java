package ink.ziip.championshipscore.database.sync;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseSyncEventTest {
    @Test
    void fieldsRoundTripWithoutLosingDomains() {
        DatabaseSyncEvent event = new DatabaseSyncEvent(UUID.randomUUID(), "core-a", 1234L,
                EnumSet.of(DatabaseSyncDomain.TEAM, DatabaseSyncDomain.RANK), "team-member-moved");
        assertEquals(event, DatabaseSyncEvent.parse(event.fields()));
    }

    @Test
    void unknownSchemaIsRejectedInsteadOfBeingMisapplied() {
        DatabaseSyncEvent event = new DatabaseSyncEvent(UUID.randomUUID(), "core-a", 1234L,
                EnumSet.of(DatabaseSyncDomain.PLAYER), "player-created");
        HashMap<String, String> fields = new HashMap<>(event.fields());
        fields.put("schema", "99");
        assertThrows(IllegalArgumentException.class, () -> DatabaseSyncEvent.parse(fields));
    }
}

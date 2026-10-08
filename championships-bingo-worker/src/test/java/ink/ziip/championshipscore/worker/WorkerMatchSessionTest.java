package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class WorkerMatchSessionTest {

    @Test
    void createsStablePortableIdsForProtocolTeams() {
        assertEquals("ccb_0", WorkerMatchSession.nativeTeamId(0));
        assertEquals("ccb_z", WorkerMatchSession.nativeTeamId(35));
        assertTrue(WorkerMatchSession.nativeTeamId(Integer.MAX_VALUE).length() <= 16);
        assertThrows(IllegalArgumentException.class, () -> WorkerMatchSession.nativeTeamId(-1));
    }
}

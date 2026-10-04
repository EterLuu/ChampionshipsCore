package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorkerVisibilityManagerTest {
    @Test
    void spectatorsSeeBothPlayersAndOtherSpectators() {
        assertTrue(WorkerVisibilityManager.allows(true, true, true));
        assertTrue(WorkerVisibilityManager.allows(true, true, false));
        assertTrue(WorkerVisibilityManager.allows(false, true, true));
    }

    @Test
    void participantsNeverSeeSpectatorEntities() {
        assertFalse(WorkerVisibilityManager.allows(true, false, true));
        assertTrue(WorkerVisibilityManager.allows(true, false, false));
    }

    @Test
    void unjoinedViewersKeepNormalLobbyPolicy() {
        assertTrue(WorkerVisibilityManager.allows(false, false, true));
        assertTrue(WorkerVisibilityManager.allows(false, false, false));
    }
}

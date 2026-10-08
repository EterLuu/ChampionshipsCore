package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorkerVisibilityManagerTest {
    @Test
    void visibilityTruthTableKeepsSpectatorMutualVisibilityAndParticipantProtection() {
        boolean[][] cases = {
            {false, false, false, true}, {false, false, true, true},
            {false, true, false, true}, {false, true, true, true},
            {true, false, false, true}, {true, false, true, false},
            {true, true, false, true}, {true, true, true, true}
        };
        for (boolean[] example : cases) {
            assertEquals(
                    example[3],
                    WorkerVisibilityManager.allows(example[0], example[1], example[2]),
                    java.util.Arrays.toString(example));
        }
    }
}

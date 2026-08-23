package ink.ziip.championshipscore.api.game.parkourwarrior;

import ink.ziip.championshipscore.api.object.game.parkourwarrior.PKWFinalCheckPointTypeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParkourWarriorDailyMultiplierTest {
    @Test
    void dailyFinalDifficultyUsesPersonalAbsoluteMultipliers() {
        assertEquals(1D, ParkourWarriorTeamArea.dailyFinalPointMultiplier(PKWFinalCheckPointTypeEnum.none));
        assertEquals(1D, ParkourWarriorTeamArea.dailyFinalPointMultiplier(PKWFinalCheckPointTypeEnum.easy));
        assertEquals(1.5D, ParkourWarriorTeamArea.dailyFinalPointMultiplier(PKWFinalCheckPointTypeEnum.normal));
        assertEquals(2.5D, ParkourWarriorTeamArea.dailyFinalPointMultiplier(PKWFinalCheckPointTypeEnum.hard));
    }

    @Test
    void dailyTimeoutHalvesOnlyUnfinishedPlayers() {
        assertEquals(0.5D, ParkourWarriorTeamArea.dailyCompletionMultiplier(false, true));
        assertEquals(1D, ParkourWarriorTeamArea.dailyCompletionMultiplier(true, true));
        assertEquals(1D, ParkourWarriorTeamArea.dailyCompletionMultiplier(false, false));
    }
}

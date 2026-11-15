package ink.ziip.championshipscore.api.game.parkourwarrior.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.parkourwarrior.model.PKWFinalCheckPointTypeEnum;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ParkourWarriorRulesTest {
    @ParameterizedTest(name = "{0} final -> x{1}")
    @CsvSource({"none, 1", "easy, 1", "normal, 1.5", "hard, 2.5"})
    void dailyFinalDifficultyUsesPersonalAbsoluteMultipliers(
            PKWFinalCheckPointTypeEnum type, double multiplier) {
        assertEquals(multiplier, ParkourWarriorTeamArea.dailyFinalPointMultiplier(type));
    }

    @ParameterizedTest(name = "finished={0} timedOut={1} -> x{2}")
    @CsvSource({"false, true, 0.5", "true, true, 1", "false, false, 1"})
    void dailyTimeoutHalvesOnlyUnfinishedPlayers(
            boolean finished, boolean timedOut, double multiplier) {
        assertEquals(
                multiplier, ParkourWarriorTeamArea.dailyCompletionMultiplier(finished, timedOut));
    }

    @ParameterizedTest(name = "{0} completed={1} -> spectator={2}")
    @CsvSource({
        "PROGRESS, true, true",
        "PROGRESS, false, false",
        "COUNTDOWN, true, false",
        "END, true, false"
    })
    void reconnectingCompletedRunnerStaysSpectatorOnlyWhileMatchIsInProgress(
            GameStageEnum stage, boolean completed, boolean spectator) {
        assertEquals(
                spectator,
                ParkourWarriorTeamArea.shouldRestoreFinishedPlayerAsSpectator(stage, completed));
    }
}

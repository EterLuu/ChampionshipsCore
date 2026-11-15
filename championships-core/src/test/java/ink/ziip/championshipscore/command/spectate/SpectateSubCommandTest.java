package ink.ziip.championshipscore.command.spectate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

class SpectateSubCommandTest {
    @Test
    void firstArgumentListsEnabledGamesAndLeaveIndependentOfLiveInstances() {
        assertEquals(
                List.of("bingo", "leave", "tntrun"),
                SpectateSubCommand.firstArgumentCompletions(
                        EnumSet.of(GameTypeEnum.Bingo, GameTypeEnum.TNTRun), ""));
        assertEquals(
                List.of("bingo"),
                SpectateSubCommand.firstArgumentCompletions(
                        EnumSet.of(GameTypeEnum.Bingo, GameTypeEnum.BattleBox), "bi"));
        assertEquals(
                List.of(),
                SpectateSubCommand.firstArgumentCompletions(
                        EnumSet.of(GameTypeEnum.BattleBox), "bi"));
    }
}

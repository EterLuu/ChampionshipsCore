package ink.ziip.championshipscore.command.spectate;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectateCompletionTest {
    @Test
    void enabledBingoAppearsWithoutAnyLiveMatch() {
        var completions = SpectateSubCommand.firstArgumentCompletions(
                EnumSet.of(GameTypeEnum.Bingo, GameTypeEnum.TNTRun), "");

        assertTrue(completions.contains("bingo"));
        assertTrue(completions.contains("tntrun"));
        assertTrue(completions.contains("leave"));
    }

    @Test
    void prefixFilteringAndEnabledGamesAreIndependentOfInstances() {
        var completions = SpectateSubCommand.firstArgumentCompletions(
                EnumSet.of(GameTypeEnum.Bingo, GameTypeEnum.BattleBox), "bi");

        assertEquals(java.util.List.of("bingo"), completions);
        assertFalse(SpectateSubCommand.firstArgumentCompletions(
                EnumSet.of(GameTypeEnum.BattleBox), "bi").contains("bingo"));
    }
}

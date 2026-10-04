package ink.ziip.championshipscore.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

class GameTabCompletionTest {
    @Test
    void gameNamesContainOnlyEnabledGames() {
        assertEquals(
                List.of("bingo", "tntrun"),
                GameTabCompletion.gameNames(EnumSet.of(GameTypeEnum.TNTRun, GameTypeEnum.Bingo)));
    }

    @Test
    void mapsDisappearWhenTheirGameIsDisabled() {
        List<String> maps = List.of("towny", "factory");

        assertEquals(
                List.of(),
                GameTabCompletion.mapNames(
                        GameTypeEnum.ParkourTag, EnumSet.of(GameTypeEnum.Bingo), maps));
        assertEquals(
                List.of("factory", "towny"),
                GameTabCompletion.mapNames(
                        GameTypeEnum.ParkourTag,
                        EnumSet.of(GameTypeEnum.Bingo, GameTypeEnum.ParkourTag),
                        maps));
    }
}

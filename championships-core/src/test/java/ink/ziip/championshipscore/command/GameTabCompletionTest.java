package ink.ziip.championshipscore.command;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameTabCompletionTest {
    @Test
    void gameNamesContainOnlyEnabledGames() {
        assertEquals(List.of("bingo", "tntrun"), GameTabCompletion.gameNames(
                EnumSet.of(GameTypeEnum.TNTRun, GameTypeEnum.Bingo)));
    }

    @Test
    void mapsDisappearWhenTheirGameIsDisabled() {
        List<String> maps = List.of("towny", "factory");

        assertEquals(List.of(), GameTabCompletion.mapNames(GameTypeEnum.ParkourTag,
                EnumSet.of(GameTypeEnum.Bingo), maps));
        assertEquals(List.of("factory", "towny"), GameTabCompletion.mapNames(GameTypeEnum.ParkourTag,
                EnumSet.of(GameTypeEnum.Bingo, GameTypeEnum.ParkourTag), maps));
    }
}

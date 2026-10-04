package ink.ziip.championshipscore.api.game.start;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameStageEnum;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;
import ink.ziip.championshipscore.api.game.parkourtag.runtime.ParkourTagArea;

import org.junit.jupiter.api.Test;

import java.util.List;

class StartTargetsTest {
    @Test
    void automaticPairAllocationSkipsBusyCopiesAndUsesStablePhysicalIds() throws Exception {
        var first = slot(0, GameStageEnum.PROGRESS);
        var second = slot(1, GameStageEnum.WAITING);
        var third = slot(2, GameStageEnum.WAITING);
        var fourth = slot(3, GameStageEnum.WAITING);
        assertEquals(
                List.of(second, third, fourth),
                StartTargets.copies(
                        List.of(fourth, third, first, second), ArenaSelection.all(), 3));
        assertEquals(
                List.of(fourth, second),
                StartTargets.copies(
                        List.of(first, second, third, fourth), ArenaSelection.parse("4,2"), 2));
        assertEquals(GameStageEnum.PROGRESS, first.getGameStageEnum());
    }

    @Test
    void anUnavailableExplicitCopyDoesNotFallBackOrYieldAPartialBatch() throws Exception {
        var first = slot(0, GameStageEnum.PROGRESS);
        var second = slot(1, GameStageEnum.WAITING);
        assertThrows(
                IllegalArgumentException.class,
                () -> StartTargets.copies(List.of(first, second), ArenaSelection.parse("1,2"), 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> StartTargets.copies(List.of(first, second), ArenaSelection.all(), 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> StartTargets.copies(List.of(first, second), ArenaSelection.parse("3"), 1));
    }

    @Test
    void aSingleMatchReplicaCannotSilentlyIgnoreExtraSelectedIndices() throws Exception {
        var first = slot(0, GameStageEnum.WAITING);
        var second = slot(1, GameStageEnum.WAITING);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        StartTargets.forGame(
                                GameTypeEnum.AceRace,
                                List.of(first, second),
                                4,
                                ArenaSelection.parse("1,2")));
        assertEquals(
                List.of(second),
                StartTargets.forGame(
                        GameTypeEnum.AceRace,
                        List.of(first, second),
                        4,
                        ArenaSelection.parse("2")));
    }

    private static Slot slot(int index, GameStageEnum stage) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var result = (Slot) ((sun.misc.Unsafe) field.get(null)).allocateInstance(Slot.class);
        result.index = index;
        result.stage = stage;
        return result;
    }

    private static final class Slot extends ParkourTagArea {
        private int index;
        private GameStageEnum stage;

        private Slot() {
            super(null, null);
        }

        @Override
        public int getCopyIndex() {
            return index;
        }

        @Override
        public GameStageEnum getGameStageEnum() {
            return stage;
        }
    }
}

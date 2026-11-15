package ink.ziip.championshipscore.api.game.bingo.execution;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.protocol.BingoDifficulty;
import ink.ziip.championshipscore.protocol.BingoMode;
import ink.ziip.championshipscore.protocol.BingoRemix;
import ink.ziip.championshipscore.protocol.BingoVariantRules;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Set;

class BingoStartRequestTest {
    @ParameterizedTest
    @EnumSource(GameRunMode.class)
    void onlyEventShapedRunsShowIntroductionAndOnlyTestOrDailyRunsKeepExplicitVariants(
            GameRunMode mode) {
        var variant =
                new BingoVariantRules(BingoMode.POINTS, BingoDifficulty.HARD, 1, BingoRemix.NETHER);
        var request = new BingoStartRequest("bingo", true, mode, List.of(), variant);
        boolean introduction = Set.of(GameRunMode.EVENT, GameRunMode.TEST_EVENT).contains(mode);
        boolean explicitVariant =
                Set.of(GameRunMode.DAILY, GameRunMode.TEST, GameRunMode.TEST_EVENT).contains(mode);
        assertAll(
                mode.name(),
                () -> assertEquals(mode, request.runMode()),
                () -> assertEquals(introduction, request.showIntroduction()),
                () ->
                        assertEquals(
                                explicitVariant ? variant : BingoVariantRules.FIXED_POINTS,
                                request.variant()));
    }
}

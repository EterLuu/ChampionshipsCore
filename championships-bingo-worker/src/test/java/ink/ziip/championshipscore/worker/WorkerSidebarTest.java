package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.bingo.engine.BingoResult;
import ink.ziip.championshipscore.protocol.*;
import ink.ziip.championshipscore.worker.support.WorkerPresentationFixture;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;

class WorkerSidebarTest {
    private static final BingoResult RESULT =
            new BingoResult(
                    1,
                    false,
                    Map.of(1, 30, 2, 10),
                    Map.of(1, 3, 2, 1),
                    Map.of(1, 1L, 2, 2L),
                    "test");

    private static String text(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static List<String> lines(WorkerSidebar.Frame frame) {
        return frame.lines().stream().map(WorkerSidebarTest::text).toList();
    }

    private static WorkerSidebar sidebar(MatchManifest m) {
        return new WorkerSidebar(m, warning -> fail(warning));
    }

    @Test
    void configuredFrameUsesViewerTasksAndOwnRankingWithoutLeakingToSpectators() {
        var m =
                WorkerPresentationFixture.manifest(
                        3,
                        BingoRemix.NONE,
                        Map.of(
                                "sidebar.title",
                                "&aCustom",
                                "sidebar.line-count",
                                "3",
                                "sidebar.line.0",
                                "{game.name} {game.status} {viewer.tasks}",
                                "sidebar.line.1",
                                "{ranking}",
                                "sidebar.line.2",
                                "",
                                "sidebar.ranking-line",
                                "{rank.position} {rank.team} {rank.score} {rank.tasks}",
                                "sidebar.own-ranking-line",
                                "Own {rank.position} {rank.team} {rank.score} {rank.tasks}"));
        var sidebar = sidebar(m);
        var player = sidebar.frame(m.participants().get(1), RESULT, MatchState.RUNNING);
        assertEquals("Custom", text(player.title()));
        assertEquals(
                List.of("Bingo progress 1", "1 Team1 30 3", "Own 2 Team2 10 1", ""), lines(player));
        var spectator = sidebar.frame(m.participants().get(2), RESULT, MatchState.RUNNING);
        assertEquals(
                List.of("Bingo progress 0", "1 Team1 30 3", "2 Team2 10 1", ""), lines(spectator));
        assertThrows(UnsupportedOperationException.class, () -> player.lines().clear());
    }

    @Test
    void legacyManifestRetainsLayoutRankingsAndCardSize() {
        var m = WorkerPresentationFixture.manifest(5, BingoRemix.NONE, Map.of());
        var frame = sidebar(m).frame(m.participants().getFirst(), RESULT, MatchState.FINISHED);
        assertEquals("Legacy title", text(frame.title()));
        assertEquals(
                List.of(
                        "---",
                        "Game Bingo",
                        "State end",
                        "Teams",
                        "Own 1:Team1:30:3/25",
                        "2:Team2:10:1/25",
                        "",
                        "Footer"),
                lines(frame));
    }

    @Test
    void explicitEmptyTitleAndLinesDoNotFallBackToLegacy() {
        var m =
                WorkerPresentationFixture.manifest(
                        3, BingoRemix.NONE, Map.of("sidebar.title", "", "sidebar.line-count", "0"));
        var frame = sidebar(m).frame(m.participants().getFirst(), RESULT, MatchState.RUNNING);
        assertEquals("", text(frame.title()));
        assertTrue(frame.lines().isEmpty());
    }

    @Test
    void malformedHistoricalLineCountRetainsEmptyBoardFallback() {
        var m =
                WorkerPresentationFixture.manifest(
                        3, BingoRemix.NONE, Map.of("sidebar.line-count", "broken"));
        var frame = sidebar(m).frame(m.participants().getFirst(), RESULT, MatchState.RUNNING);
        assertEquals("Legacy title", text(frame.title()));
        assertTrue(frame.lines().isEmpty());
    }

    @Test
    void configuredLinesAreLimitedToFifteenAfterExpansion() {
        var values = new HashMap<String, String>();
        values.put("sidebar.line-count", "16");
        for (int i = 0; i < 16; i++) values.put("sidebar.line." + i, "Line" + i);
        var m = WorkerPresentationFixture.manifest(3, BingoRemix.NONE, values);
        var frame = sidebar(m).frame(m.participants().getFirst(), RESULT, MatchState.RUNNING);
        assertEquals(15, frame.lines().size());
        assertEquals("Line14", text(frame.lines().getLast()));
    }

    @ParameterizedTest
    @EnumSource(MatchState.class)
    void statesUseTheFrozenManifestLabels(MatchState state) {
        var m =
                WorkerPresentationFixture.manifest(
                        3,
                        BingoRemix.NONE,
                        Map.of("sidebar.line-count", "1", "sidebar.line.0", "{game.status}"));
        var frame = sidebar(m).frame(m.participants().getFirst(), RESULT, state);
        assertEquals(
                List.of(
                        WorkerPresentationService.sidebarStatus(
                                m.runtimeRules().presentation(), state)),
                lines(frame));
    }

    @Test
    void ordinaryFieldValuesAreNotReinterpretedAsOtherTokens() {
        var m =
                WorkerPresentationFixture.manifest(
                        3,
                        BingoRemix.NONE,
                        Map.of(
                                "game.name",
                                "{viewer.tasks}",
                                "sidebar.line-count",
                                "1",
                                "sidebar.line.0",
                                "{game.name} {viewer.tasks}"));
        assertEquals(
                List.of("{viewer.tasks} 3"),
                lines(sidebar(m).frame(m.participants().getFirst(), RESULT, MatchState.RUNNING)));
    }
}

package ink.ziip.championshipscore.api.game.bingo.game;

import static ink.ziip.championshipscore.support.TestObjects.proxy;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.bingo.card.CardSize;
import ink.ziip.championshipscore.api.game.bingo.task.EventTask;
import ink.ziip.championshipscore.api.game.bingo.task.GameTask;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticHandle;
import ink.ziip.championshipscore.api.game.bingo.task.StatisticTask;
import ink.ziip.championshipscore.api.game.bingo.task.TaskData;
import ink.ziip.championshipscore.api.game.bingo.task.pool.Dimension;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import ink.ziip.championshipscore.bingo.engine.BingoScoringEngine;
import ink.ziip.championshipscore.protocol.*;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;

class BingoCompletionScoreTest {
    private static final UUID RED = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MATE = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BLUE = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final int[] POINTS = {30, 20, 10};

    @Test
    void consecutiveCompletionsKeepTheirOwnClaimRankLineBonusAndFrozenAwards() {
        var f = new Fixture(CardSize.X3, BingoMode.POINTS, BingoRemix.NONE);
        f.complete(RED, 0, 1).orElseThrow();
        f.complete(RED, 1, 2).orElseThrow();
        var first = f.complete(RED, 2, 3).orElseThrow();
        f.complete(BLUE, 3, 4).orElseThrow();
        var second = f.complete(RED, 3, 5).orElseThrow();
        assertEquals(30, first.cellDelta());
        assertEquals(50, first.lineDelta());
        assertEquals(20, second.cellDelta());
        assertEquals(0, second.lineDelta());
        assertEquals(210, f.round.score(f.red));
        assertEquals(Map.of(RED, 80, MATE, 50), awards(first));
        assertEquals(Map.of(RED, 20), awards(second));
        assertSame(f.round.card().getTasks().get(2), first.task());
        assertSame(f.round.card().getTasks().get(3), second.task());
        assertTrue(f.complete(RED, 2, 6).isEmpty());
        assertEquals(210, f.round.score(f.red));
    }

    @Test
    void aSingleStatisticScanReturnsIndependentDecisionsForEveryCompletedCell() {
        var f = new Fixture(CardSize.X3, BingoMode.POINTS, BingoRemix.NONE);
        f.complete(RED, 0, 1).orElseThrow();
        f.complete(RED, 1, 2).orElseThrow();
        f.round.card().getTasks().get(2).data =
                new StatisticTask(new StatisticHandle(Statistic.JUMP), 2);
        f.round.card().getTasks().get(3).data =
                new StatisticTask(new StatisticHandle(Statistic.DEATHS), 1);
        assertEquals(1, f.round.tryCompleteStatistics(statisticPlayer(BLUE, 0), f.blue, 3).size());
        var batch = f.round.tryCompleteStatistics(statisticPlayer(RED, 2), f.red, 4);
        assertEquals(
                List.of(30, 20),
                batch.stream().map(BingoRound.CompletionResult::cellDelta).toList());
        assertEquals(
                List.of(50, 0),
                batch.stream().map(BingoRound.CompletionResult::lineDelta).toList());
        assertEquals(Map.of(RED, 80, MATE, 50), awards(batch.getFirst()));
        assertEquals(Map.of(RED, 20), awards(batch.getLast()));
        assertEquals(210, f.round.score(f.red));
        assertTrue(f.round.tryCompleteStatistics(statisticPlayer(RED, 2), f.red, 5).isEmpty());
    }

    private static Player statisticPlayer(UUID id, int jumps) {
        return proxy(
                Player.class,
                (p, m, a) ->
                        switch (m.getName()) {
                            case "getUniqueId" -> id;
                            case "getName" -> id.toString();
                            case "getStatistic" -> a[0] == Statistic.JUMP ? jumps : 1;
                            default -> throw new AssertionError(m.getName());
                        });
    }

    @ParameterizedTest
    @EnumSource(CardSize.class)
    void localAndRemoteAgreeAcrossAllModesAndScoringRemixes(CardSize size) {
        for (var mode : BingoMode.values()) {
            for (var remix :
                    List.of(
                            BingoRemix.NONE,
                            BingoRemix.COOP,
                            BingoRemix.CHAIN,
                            BingoRemix.DIFFERENTIAL,
                            BingoRemix.PARALLAX,
                            BingoRemix.BLIND)) {
                var f = new Fixture(size, mode, remix);
                long sequence = 0;
                for (int cell = 0; cell < size.fullCardSize; cell++) {
                    for (UUID player : List.of(RED, BLUE)) {
                        int id = player.equals(RED) ? 0 : 1;
                        long time = ++sequence;
                        var remote =
                                f.remote.apply(
                                        new CompletionObservation(
                                                f.matchId, 1, sequence, id, player, cell, time));
                        var local = f.complete(player, cell, time);
                        String context = size + "/" + mode + "/" + remix + "/" + cell + "/" + id;
                        assertEquals(remote.accepted(), local.isPresent(), context);
                        local.ifPresent(
                                completion -> {
                                    assertEquals(
                                            remote.cellPoints(), completion.cellDelta(), context);
                                    assertEquals(
                                            remote.linePointsPerMember(),
                                            completion.lineDelta(),
                                            context);
                                    assertEquals(remote.awards(), completion.awards(), context);
                                });
                        for (var team : List.of(f.red, f.blue)) {
                            int teamId = team == f.red ? 0 : 1;
                            assertEquals(f.remote.score(teamId), f.round.score(team), context);
                            assertEquals(
                                    f.remote.completedCount(teamId),
                                    f.round.completedCount(team),
                                    context);
                            assertEquals(
                                    f.remote.completedLines(teamId),
                                    f.round.countCompletedLines(team),
                                    context);
                            assertEquals(f.remote.hasWon(teamId), f.round.hasWon(team), context);
                            assertEquals(
                                    f.remote.lastCompletionTick(teamId),
                                    f.round.lastCompletionTime(team),
                                    context);
                            assertArrayEquals(
                                    f.remote.completedIndices(teamId),
                                    f.round.completedIndices(team),
                                    context);
                        }
                        assertEquals(
                                f.remote.boardFullyClaimed(), f.round.boardFullyClaimed(), context);
                    }
                }
            }
        }
    }

    @Test
    void wrongTeamMemberCannotMutateCardsOrScoreAndANewRoundStartsEmpty() {
        var f = new Fixture(CardSize.X3, BingoMode.POINTS, BingoRemix.NONE);
        assertTrue(
                f.round
                        .tryCompleteEventSignal(player(BLUE), f.red, "eat", "cell-0-42", 1)
                        .isEmpty());
        assertFalse(f.round.card().getTasks().getFirst().isCompleted());
        assertEquals(0, f.round.score(f.red));
        f.complete(RED, 0, 2).orElseThrow();
        var next = new Fixture(CardSize.X3, BingoMode.POINTS, BingoRemix.NONE);
        assertEquals(0, next.round.completedCount(next.red));
        assertEquals(0, next.round.score(next.red));
        assertEquals(Long.MAX_VALUE, next.round.lastCompletionTime(next.red));
    }

    @Test
    void chainAndExplicitTaskLocksRejectBeforeCommitting() {
        var f = new Fixture(CardSize.X3, BingoMode.POINTS, BingoRemix.CHAIN);
        f.complete(RED, 0, 1).orElseThrow();
        assertTrue(f.complete(RED, 8, 2).isEmpty());
        f.round.card().getTasks().get(1).setLocked(true);
        assertTrue(f.complete(RED, 1, 3).isEmpty());
        assertEquals(30, f.round.score(f.red));
        assertEquals(1, f.round.completedCount(f.red));
    }

    @Test
    void equalScoresUseCompletionTimeAndDifferentialFirstClaimsUseArrivalOrder() {
        var f = new Fixture(CardSize.X3, BingoMode.POINTS, BingoRemix.DIFFERENTIAL);
        f.complete(BLUE, 0, 1).orElseThrow();
        f.complete(RED, 1, 2).orElseThrow();
        assertEquals(List.of(f.blue, f.red), f.round.rankedTeams());
        assertSame(f.blue, f.round.resolveTopScore());
        f.complete(RED, 0, 3).orElseThrow();
        assertEquals(1, f.round.countFirstCompletions(f.blue));
        assertEquals(1, f.round.countFirstCompletions(f.red));
    }

    private static Map<UUID, Integer> awards(BingoRound.CompletionResult completion) {
        var result = new HashMap<UUID, Integer>();
        completion
                .awards()
                .forEach(award -> result.merge(award.playerId(), award.points(), Integer::sum));
        return result;
    }

    private static Player player(UUID uuid) {
        return proxy(
                Player.class,
                (p, m, a) ->
                        switch (m.getName()) {
                            case "getUniqueId" -> uuid;
                            case "getName" -> uuid.toString();
                            default -> throw new AssertionError(m.getName());
                        });
    }

    private static final class Fixture {
        final ChampionshipTeam red =
                new ChampionshipTeam(
                        -1,
                        "red",
                        "red",
                        "#ff0000",
                        new LinkedHashSet<>(List.of(RED, MATE)),
                        null) {};
        final ChampionshipTeam blue =
                new ChampionshipTeam(-2, "blue", "blue", "#0000ff", Set.of(BLUE), null) {};
        final UUID matchId = UUID.randomUUID();
        final BingoRound round;
        final BingoScoringEngine remote;

        Fixture(CardSize size, BingoMode mode, BingoRemix remix) {
            var variant = new BingoVariantRules(mode, BingoDifficulty.NORMAL, 1, remix);
            round =
                    new BingoRound(
                            size,
                            42,
                            Set.of(TaskData.TaskType.EVENT),
                            Set.of(),
                            Map.of(),
                            List.of(red, blue),
                            POINTS,
                            50,
                            2,
                            25,
                            variant,
                            settings ->
                                    java.util.stream.IntStream.range(0, size.fullCardSize)
                                            .mapToObj(
                                                    i ->
                                                            new GameTask(
                                                                    new EventTask(
                                                                            "eat",
                                                                            "cell-"
                                                                                    + i
                                                                                    + "-"
                                                                                    + settings
                                                                                            .seed(),
                                                                            1,
                                                                            Dimension.OVERWORLD)))
                                            .toList());
            var rules = new BingoScoringRules(size.size, List.of(30, 20, 10), 50, 2, 25, variant);
            var manifest =
                    new MatchManifest(
                            ProtocolVersion.CURRENT,
                            matchId,
                            1,
                            1,
                            "worker",
                            MatchRunMode.DAILY,
                            600,
                            42,
                            "config",
                            rules,
                            new BingoRuntimeRules(10, 6, 32, 180, List.of()),
                            java.util.stream.IntStream.range(0, size.fullCardSize)
                                    .mapToObj(
                                            i ->
                                                    new BingoTaskSpec(
                                                            i, "cell-" + i, "event", Map.of()))
                                    .toList(),
                            List.of(
                                    new TeamSnapshot(
                                            0,
                                            "red",
                                            "red",
                                            "#ff0000",
                                            List.copyOf(red.getMembers())),
                                    new TeamSnapshot(
                                            1,
                                            "blue",
                                            "blue",
                                            "#0000ff",
                                            List.copyOf(blue.getMembers()))),
                            List.of(
                                    new PlayerSnapshot(RED, "red", ParticipantRole.PLAYER, 0),
                                    new PlayerSnapshot(MATE, "mate", ParticipantRole.PLAYER, 0),
                                    new PlayerSnapshot(BLUE, "blue", ParticipantRole.PLAYER, 1)));
            remote = new BingoScoringEngine(manifest);
        }

        Optional<BingoRound.CompletionResult> complete(UUID player, int cell, long time) {
            var team = player.equals(BLUE) ? blue : red;
            var task =
                    (EventTask)
                            round.cardForPlayer(player, team)
                                    .orElseThrow()
                                    .getTasks()
                                    .get(cell)
                                    .data;
            return round.tryCompleteEventSignal(
                    player(player), team, task.trigger(), task.param(), time);
        }
    }
}

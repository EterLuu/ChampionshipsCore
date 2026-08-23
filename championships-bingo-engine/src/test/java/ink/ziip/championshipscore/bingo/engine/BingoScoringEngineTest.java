package ink.ziip.championshipscore.bingo.engine;

import ink.ziip.championshipscore.protocol.BingoScoringRules;
import ink.ziip.championshipscore.protocol.BingoDifficulty;
import ink.ziip.championshipscore.protocol.BingoMode;
import ink.ziip.championshipscore.protocol.BingoRemix;
import ink.ziip.championshipscore.protocol.BingoVariantRules;
import ink.ziip.championshipscore.protocol.BingoRuntimeRules;
import ink.ziip.championshipscore.protocol.BingoTaskSpec;
import ink.ziip.championshipscore.protocol.BinaryProtocolCodec;
import ink.ziip.championshipscore.protocol.CompletionObservation;
import ink.ziip.championshipscore.protocol.DeterministicIds;
import ink.ziip.championshipscore.protocol.MatchManifest;
import ink.ziip.championshipscore.protocol.MatchRunMode;
import ink.ziip.championshipscore.protocol.MatchState;
import ink.ziip.championshipscore.protocol.MatchStateMachine;
import ink.ziip.championshipscore.protocol.ParticipantRole;
import ink.ziip.championshipscore.protocol.PlayerSnapshot;
import ink.ziip.championshipscore.protocol.ProtocolVersion;
import ink.ziip.championshipscore.protocol.TeamSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BingoScoringEngineTest {
    private static final UUID MATCH_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RED_ONE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RED_TWO = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID BLUE_ONE = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void preservesClaimRankAndPerMemberLineAwards() {
        BingoScoringEngine engine = new BingoScoringEngine(manifest());

        ScoringDecision first = engine.apply(observation(1, 1, RED_ONE, 0, 20));
        ScoringDecision second = engine.apply(observation(2, 2, BLUE_ONE, 0, 21));
        ScoringDecision line = engine.apply(observation(3, 1, RED_ONE, 1, 22));

        assertTrue(first.accepted());
        assertEquals(0, first.claimRank());
        assertEquals(60, first.cellPoints());
        assertEquals(1, second.claimRank());
        assertEquals(50, second.cellPoints());

        assertEquals(50, line.linePointsPerMember());
        assertEquals(3, line.awards().size());
        assertEquals(220, line.teamScore());
        assertEquals(220, engine.result().teamScores().get(1));
        assertEquals(50, engine.result().teamScores().get(2));
    }

    @Test
    void replayIsIdempotentAndSequenceGapsAreRejected() {
        BingoScoringEngine engine = new BingoScoringEngine(manifest());
        CompletionObservation observation = observation(1, 1, RED_ONE, 0, 20);

        ScoringDecision original = engine.apply(observation);
        assertEquals(original, engine.apply(observation));
        assertEquals(60, engine.result().teamScores().get(1));

        assertThrows(IllegalStateException.class,
                () -> engine.apply(observation(3, 1, RED_ONE, 1, 21)));
        assertThrows(IllegalStateException.class,
                () -> engine.apply(observation(1, 1, RED_ONE, 1, 21)));
    }

    @Test
    void resultHashMatchesIndependentReplay() {
        CompletionObservation first = observation(1, 1, RED_ONE, 0, 20);
        CompletionObservation second = observation(2, 2, BLUE_ONE, 0, 21);
        BingoScoringEngine worker = new BingoScoringEngine(manifest());
        BingoScoringEngine core = new BingoScoringEngine(manifest());

        worker.apply(first);
        worker.apply(second);
        core.apply(first);
        core.apply(second);

        assertEquals(worker.result(), core.result());
        assertFalse(worker.result().boardFullyClaimed());
    }

    @Test
    void resultRankingReusesLocalScoreAndCompletionTimeSemantics() {
        BingoResult result = new BingoResult(3, false, Map.of(1, 60, 2, 60, 3, 0),
                Map.of(1, 1, 2, 1, 3, 0), Map.of(1, 40L, 2, 20L, 3, Long.MAX_VALUE), "hash");

        assertEquals(List.of(2, 1, 3), result.rankedTeamIds());
        assertEquals(2, result.winnerTeamId());
    }

    @Test
    void scoreTransactionIdsAreStableButNamespacedBySequence() {
        UUID first = DeterministicIds.scoreTransaction(MATCH_ID, 1, 7, RED_ONE, "cell:0");
        UUID replay = DeterministicIds.scoreTransaction(MATCH_ID, 1, 7, RED_ONE, "cell:0");
        UUID next = DeterministicIds.scoreTransaction(MATCH_ID, 1, 8, RED_ONE, "cell:0");

        assertEquals(first, replay);
        assertNotEquals(first, next);
        assertEquals(5, first.version());
    }

    @Test
    void lifecycleRejectsBackwardsTransitionsAndCanResumeItsPreviousState() {
        MatchStateMachine lifecycle = new MatchStateMachine();
        lifecycle.transitionTo(MatchState.PREPARING);
        lifecycle.transitionTo(MatchState.READY);
        lifecycle.transitionTo(MatchState.ROUTING);
        lifecycle.transitionTo(MatchState.SUSPENDED);

        assertEquals(MatchState.ROUTING, lifecycle.resume().to());
        assertThrows(IllegalStateException.class, () -> lifecycle.transitionTo(MatchState.READY));
        lifecycle.transitionTo(MatchState.ABORTED);
        assertTrue(lifecycle.state().terminal());
        assertThrows(IllegalStateException.class, () -> lifecycle.transitionTo(MatchState.PREPARING));
    }

    @Test
    void manifestBinaryCodecRoundTripsWithoutPlatformTypes() {
        BinaryProtocolCodec codec = new BinaryProtocolCodec();
        MatchManifest manifest = manifest();

        assertEquals(manifest, codec.decodeManifest(codec.encodeManifest(manifest)));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeManifest(new byte[]{1, 2, 3}));
    }

    @Test
    void dominationLocksAClaimedCellAndSpeedrunEndsOnConfiguredLines() {
        BingoScoringEngine domination = new BingoScoringEngine(manifest(
                new BingoVariantRules(BingoMode.DOMINATION, BingoDifficulty.NORMAL, 1, BingoRemix.NONE)));
        assertTrue(domination.apply(observation(1, 1, RED_ONE, 0, 1)).accepted());
        assertFalse(domination.apply(observation(2, 2, BLUE_ONE, 0, 2)).accepted());

        BingoScoringEngine speedrun = new BingoScoringEngine(manifest(
                new BingoVariantRules(BingoMode.SPEEDRUN, BingoDifficulty.NORMAL, 1, BingoRemix.NONE)));
        speedrun.apply(observation(1, 1, RED_ONE, 0, 1));
        speedrun.apply(observation(2, 1, RED_ONE, 1, 2));
        assertTrue(speedrun.hasWon(1));
        assertEquals(2, speedrun.result().teamScores().get(1));
    }

    @Test
    void chainRequiresAdjacencyAndCoopSharesTheWholeCard() {
        BingoScoringEngine chain = new BingoScoringEngine(manifest(
                new BingoVariantRules(BingoMode.QUANTITY, BingoDifficulty.NORMAL, 1, BingoRemix.CHAIN)));
        assertTrue(chain.apply(observation(1, 1, RED_ONE, 0, 1)).accepted());
        assertFalse(chain.apply(observation(2, 1, RED_ONE, 3, 2)).accepted());

        BingoScoringEngine coop = new BingoScoringEngine(manifest(
                new BingoVariantRules(BingoMode.POINTS, BingoDifficulty.NORMAL, 1, BingoRemix.COOP)));
        coop.apply(observation(1, 1, RED_ONE, 0, 1));
        assertEquals(1, coop.result().completedCells().get(1));
        assertEquals(1, coop.result().completedCells().get(2));
    }

    private static CompletionObservation observation(
            long seq, int teamId, UUID playerId, int cellIndex, long tick) {
        return new CompletionObservation(MATCH_ID, 1, seq, teamId, playerId, cellIndex, tick);
    }

    private static MatchManifest manifest() {
        TeamSnapshot red = new TeamSnapshot(1, "red", "RED", "#ff0000", List.of(RED_ONE, RED_TWO));
        TeamSnapshot blue = new TeamSnapshot(2, "blue", "BLUE", "#0000ff", List.of(BLUE_ONE));
        return new MatchManifest(
                ProtocolVersion.CURRENT,
                MATCH_ID,
                1,
                1_700_000_000_000L,
                "scc-bingo-1",
                MatchRunMode.EVENT,
                600,
                42,
                "config-hash",
                new BingoScoringRules(2, List.of(60, 50, 40), 50, 4, 25),
                new BingoRuntimeRules(10, 6, 32, 180, List.of("night_vision:1")),
                List.of(
                        new BingoTaskSpec(0, "task-0", "item", Map.of("item", "minecraft:stone")),
                        new BingoTaskSpec(1, "task-1", "item", Map.of("item", "minecraft:dirt")),
                        new BingoTaskSpec(2, "task-2", "item", Map.of("item", "minecraft:oak_log")),
                        new BingoTaskSpec(3, "task-3", "item", Map.of("item", "minecraft:iron_ingot"))
                ),
                List.of(red, blue),
                List.of(
                        new PlayerSnapshot(RED_ONE, "RedOne", ParticipantRole.PLAYER, 1),
                        new PlayerSnapshot(RED_TWO, "RedTwo", ParticipantRole.PLAYER, 1),
                        new PlayerSnapshot(BLUE_ONE, "BlueOne", ParticipantRole.PLAYER, 2)
                ));
    }

    private static MatchManifest manifest(BingoVariantRules variant) {
        MatchManifest base = manifest();
        BingoScoringRules rules = new BingoScoringRules(base.scoring().cardWidth(),
                base.scoring().claimPoints(), base.scoring().lineBonus(),
                base.scoring().lineBonusMajorCount(), base.scoring().lineBonusMinor(), variant);
        return new MatchManifest(base.protocolVersion(), base.matchId(), base.epoch(),
                base.createdAtEpochMilli(), base.workerId(), MatchRunMode.DAILY,
                base.durationSeconds(), base.cardSeed(), base.configHash(), rules,
                base.runtimeRules(), base.tasks(), base.teams(), base.participants());
    }
}

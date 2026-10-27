package ink.ziip.championshipscore.worker;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.protocol.*;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

class WorkerTaskCompletionTest {
    @Test
    void observationsFromRemovedOrReplacedRolesCannotReachScoringOrCompletionHistory()
            throws Exception {
        var original = new PlayerSnapshot(UUID.randomUUID(), "Original", ParticipantRole.PLAYER, 2);
        var session =
                (WorkerMatchSession)
                        ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                                .allocateInstance(WorkerMatchSession.class);
        var lifecycle = new MatchStateMachine();
        field(MatchStateMachine.class, "state").set(lifecycle, MatchState.RUNNING);
        set(session, "lifecycle", lifecycle);
        var roster = new HashMap<UUID, PlayerSnapshot>();
        set(session, "participants", roster);
        var accept =
                WorkerMatchSession.class.getDeclaredMethod(
                        "acceptCompletion", PlayerSnapshot.class, int.class);
        accept.setAccessible(true);
        // Scoring and history are intentionally absent: stale observations must return first.
        accept.invoke(session, original, 0);
        for (var role : ParticipantRole.values()) {
            roster.put(
                    original.uuid(),
                    new PlayerSnapshot(
                            original.uuid(),
                            "Original",
                            role,
                            role == ParticipantRole.PLAYER ? 2 : null));
            accept.invoke(session, original, 0);
        }
        roster.put(original.uuid(), original);
        field(MatchStateMachine.class, "state").set(lifecycle, MatchState.FINISHED);
        accept.invoke(session, original, 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptedCooperativeCreditKeepsOriginalIdentityTimeAndClaimOrderAfterRosterRemoval()
            throws Exception {
        var player = new PlayerSnapshot(UUID.randomUUID(), "Original", ParticipantRole.PLAYER, 2);
        var teams = new LinkedHashMap<Integer, TeamSnapshot>();
        teams.put(1, new TeamSnapshot(1, "First", "red", "#ff0000", List.of(), 0));
        teams.put(2, new TeamSnapshot(2, "Second", "blue", "#0000ff", List.of(player.uuid()), 0));
        var third = new TeamSnapshot(3, "Third", "green", "#00ff00", List.of(), 0);
        var sourceTeams = List.of(teams.get(1), teams.get(2), third);
        teams.put(3, third);
        // Deliberately reverse the lookup map; source order belongs to manifest.teams().
        var lookup = new LinkedHashMap<Integer, TeamSnapshot>();
        for (int id : List.of(3, 2, 1)) lookup.put(id, teams.get(id));
        var tasks =
                java.util.stream.IntStream.range(0, 9)
                        .mapToObj(
                                i ->
                                        new BingoTaskSpec(
                                                i,
                                                "item-" + i,
                                                "item",
                                                Map.of("material", "STONE", "count", "1")))
                        .toList();
        var scoring =
                new BingoScoringRules(
                        3,
                        List.of(40, 30),
                        50,
                        3,
                        10,
                        new BingoVariantRules(
                                BingoMode.POINTS, BingoDifficulty.NORMAL, 1, BingoRemix.COOP));
        var runtime =
                new BingoRuntimeRules(
                        5,
                        64,
                        16,
                        0,
                        List.of(),
                        false,
                        45,
                        List.of(),
                        new BingoPresentation(Map.of()));
        var manifest =
                new MatchManifest(
                        ProtocolVersion.CURRENT,
                        UUID.randomUUID(),
                        1,
                        1,
                        "worker",
                        MatchRunMode.GAME,
                        900,
                        42,
                        "hash",
                        scoring,
                        runtime,
                        tasks,
                        sourceTeams,
                        List.of(player));
        var session =
                (WorkerMatchSession)
                        ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                                .allocateInstance(WorkerMatchSession.class);
        var facts = new HashMap<Integer, List<WorkerTaskCompletion>>();
        var roster = new HashMap<>(Map.of(player.uuid(), player));
        set(session, "manifest", manifest);
        set(session, "teams", lookup);
        set(session, "participants", roster);
        set(session, "completedTeamCells", new HashSet<>());
        set(session, "completionsByCell", facts);
        var record =
                WorkerMatchSession.class.getDeclaredMethod(
                        "recordTaskCompletion", PlayerSnapshot.class, CompletionObservation.class);
        record.setAccessible(true);
        record.invoke(
                session,
                player,
                new CompletionObservation(manifest.matchId(), 1, 1, 2, player.uuid(), 0, 147680));
        roster.clear();
        assertEquals(List.of(2, 1, 3), session.completionSnapshot().get(0));
        var frozen = session.completionSnapshot();
        assertThrows(UnsupportedOperationException.class, () -> frozen.get(0).clear());
        for (var completion : facts.get(0)) {
            assertEquals(player.uuid(), completion.playerId());
            assertEquals(
                    "Original <Second>",
                    PlainTextComponentSerializer.plainText().serialize(completion.playerName()));
            assertEquals(7384, completion.seconds());
        }
        facts.get(0).clear();
        assertEquals(List.of(2, 1, 3), frozen.get(0));
    }

    private static Field field(Class<?> type, String key) throws Exception {
        var field = type.getDeclaredField(key);
        field.setAccessible(true);
        return field;
    }

    private static void set(Object target, String key, Object value) throws Exception {
        field(target.getClass(), key).set(target, value);
    }
}

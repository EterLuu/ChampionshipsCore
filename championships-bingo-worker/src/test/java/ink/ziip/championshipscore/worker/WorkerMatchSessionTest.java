package ink.ziip.championshipscore.worker;

import static ink.ziip.championshipscore.worker.support.TestObjects.allocate;
import static ink.ziip.championshipscore.worker.support.TestObjects.field;
import static ink.ziip.championshipscore.worker.support.TestObjects.proxy;
import static ink.ziip.championshipscore.worker.support.TestObjects.set;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.bingo.engine.BingoScoringEngine;
import ink.ziip.championshipscore.platform.bukkit.scheduler.PlatformScheduler;
import ink.ziip.championshipscore.protocol.*;
import ink.ziip.championshipscore.protocol.transport.DeliveryReceipt;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

class WorkerMatchSessionTest {
    @Test
    void spectatorTemplateArgumentsPreserveFrozenPrefixAsLiteralText() throws Exception {
        var session = allocate(WorkerMatchSession.class);
        set(
                session,
                "manifest",
                presentationManifest(
                        MatchRunMode.DAILY,
                        Map.of("prefix", "&dPrefix %speed% "),
                        List.of(),
                        List.of()));
        var player =
                (org.bukkit.entity.Player)
                        java.lang.reflect.Proxy.newProxyInstance(
                                org.bukkit.entity.Player.class.getClassLoader(),
                                new Class<?>[] {org.bukkit.entity.Player.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("getFlySpeed")) return .15F;
                                    throw new AssertionError(method.getName());
                                });
        var method =
                WorkerMatchSession.class.getDeclaredMethod(
                        "spectatorControlValues", org.bukkit.entity.Player.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var values = (Map<String, String>) method.invoke(session, player);
        assertEquals(
                "&dPrefix %speed% 15%",
                ink.ziip.championshipscore.shared.presentation.MessageTemplate.render(
                        "%prefix%%speed%", values));
    }

    static Stream<Arguments> malformedFrozenPresentation() {
        var stone = Map.of("material", "STONE");
        return Stream.of(
                Arguments.of(
                        Map.of("gui.spectator-controls.speed.title", "broken"),
                        stone,
                        "gui.spectator-controls.version"),
                Arguments.of(
                        Map.of("presentation.player.prefix", "broken"),
                        stone,
                        "presentation.player.version"),
                Arguments.of(
                        Map.of(),
                        Map.of(
                                "material",
                                "STONE",
                                "display.text-version",
                                "1",
                                "display.name",
                                "{",
                                "display.lore-size",
                                "0"),
                        "frozen Bingo task text"));
    }

    @ParameterizedTest
    @MethodSource("malformedFrozenPresentation")
    void malformedFrozenPresentationFailsBeforeWorldPlayerOrInventorySetup(
            Map<String, String> messages, Map<String, String> taskAttributes, String expected) {
        var manifest =
                presentationManifest(
                        MatchRunMode.DAILY, messages, List.of(), List.of(), taskAttributes);
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new WorkerMatchSession(
                                        null, null, manifest, null, null, null, null, null));
        assertTrue(error.getMessage().contains(expected), error.getMessage());
    }

    @Test
    void legacyRootStylesMatchOldOverridesAndPreserveChildColors() throws Exception {
        var method =
                WorkerMatchSession.class.getDeclaredMethod(
                        "legacyControlStyle",
                        net.kyori.adventure.text.Component.class,
                        String.class);
        method.setAccessible(true);
        var text =
                ink.ziip.championshipscore.platform.bukkit.text.LegacyText.component(
                                "&dRoot &bChild")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        var expected =
                text.color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
        assertEquals(expected, method.invoke(null, text, "teleport.title"));
        assertEquals(
                text.color(net.kyori.adventure.text.format.NamedTextColor.GRAY),
                method.invoke(null, text, "teleport.hint"));
    }

    @Test
    void sessionAndWaitingRegistryUseFrozenFormatsAndConfiguredSpectatorGameLabel()
            throws Exception {
        var format =
                new ink.ziip.championshipscore.shared.presentation.PlayerTextFormat(
                        "{%label%}", "%game%", "&7", "%player_name%", "%message%", "%message%");
        var messages = new HashMap<>(format.attributes());
        messages.put("game.name", "Bingo %game%");
        messages.put("presentation.daily-game", "watching %game%");
        messages.put("papi.spectator", "Viewers");
        UUID player = UUID.randomUUID(), viewer = UUID.randomUUID();
        var playing = new PlayerSnapshot(player, "Alice", ParticipantRole.PLAYER, 1);
        var watching = new PlayerSnapshot(viewer, "Bob", ParticipantRole.SPECTATOR, null);
        var team = new TeamSnapshot(1, "Red", "red", "&c", List.of(player), 0);
        for (var mode : MatchRunMode.values()) {
            var manifest =
                    presentationManifest(mode, messages, List.of(team), List.of(playing, watching));
            var session = allocate(WorkerMatchSession.class);
            set(session, "manifest", manifest);
            set(session, "participants", Map.of(player, playing, viewer, watching));
            set(session, "teams", Map.of(1, team));
            var eventMode = WorkerMatchSession.class.getDeclaredMethod("eventMode");
            eventMode.setAccessible(true);
            assertEquals(mode.eventLike(), eventMode.invoke(session));
            assertEquals(format, session.playerPresentation(player).format());
            assertTrue(session.playerPresentation(player).activePlayer());
            assertEquals("&cRed", session.playerPresentation(player).label());
            assertFalse(session.playerPresentation(viewer).activePlayer());
            assertEquals(
                    mode == MatchRunMode.DAILY ? "watching Bingo %game%" : "Viewers",
                    session.playerPresentation(viewer).label());
        }
        var registry = allocate(WorkerMatchRegistry.class);
        set(registry, "latestPresentation", new BingoPresentation(messages));
        set(registry, "latestDaily", true);
        assertEquals(format, registry.playerPresentation(viewer).format());
        assertEquals("Viewers", registry.playerPresentation(viewer).label());
    }

    private static MatchManifest presentationManifest(
            MatchRunMode mode,
            Map<String, String> messages,
            List<TeamSnapshot> teams,
            List<PlayerSnapshot> players) {
        return presentationManifest(mode, messages, teams, players, Map.of("material", "STONE"));
    }

    private static MatchManifest presentationManifest(
            MatchRunMode mode,
            Map<String, String> messages,
            List<TeamSnapshot> teams,
            List<PlayerSnapshot> players,
            Map<String, String> taskAttributes) {
        return new MatchManifest(
                ProtocolVersion.CURRENT,
                UUID.randomUUID(),
                1,
                1,
                "worker",
                mode,
                60,
                1,
                "config",
                new BingoScoringRules(1, List.of(1), 0, 0, 0),
                new BingoRuntimeRules(
                        90,
                        100,
                        10,
                        130,
                        List.of(),
                        false,
                        45,
                        List.of(),
                        new BingoPresentation(messages)),
                List.of(new BingoTaskSpec(0, "stone", "item", taskAttributes)),
                teams,
                players);
    }

    @Test
    void createsStablePortableIdsForProtocolTeams() {
        assertEquals("ccb_0", WorkerMatchSession.nativeTeamId(0));
        assertEquals("ccb_z", WorkerMatchSession.nativeTeamId(35));
        assertTrue(WorkerMatchSession.nativeTeamId(Integer.MAX_VALUE).length() <= 16);
        assertThrows(IllegalArgumentException.class, () -> WorkerMatchSession.nativeTeamId(-1));
    }

    @Test
    void runningBossBarFormatsBothRoundAndProtectionTimersWithTheStrictSixtySecondBoundary()
            throws Exception {
        var messages =
                Map.of(
                        "bingo.timer",
                        "剩余%time%",
                        "bingo.pvp-protection",
                        "PvP保护%time%",
                        "bingo.pvp-active",
                        "PvP已开启");
        var rules =
                new BingoRuntimeRules(
                        90,
                        100,
                        10,
                        130,
                        List.of(),
                        false,
                        45,
                        List.of(),
                        new BingoPresentation(messages));
        var lifecycle = new MatchStateMachine();
        for (MatchState next :
                List.of(
                        MatchState.PREPARING,
                        MatchState.READY,
                        MatchState.ROUTING,
                        MatchState.COUNTDOWN,
                        MatchState.RUNNING)) lifecycle.transitionTo(next);
        var session = allocate(WorkerMatchSession.class);
        set(session, "lifecycle", lifecycle);
        set(session, "participants", Map.of());
        var update = WorkerMatchSession.class.getDeclaredMethod("updateRunningTimer");
        update.setAccessible(true);
        for (var entry : Map.of(700, "11分40秒", 61, "1分1秒", 60, "60秒").entrySet()) {
            var manifest =
                    new MatchManifest(
                            ProtocolVersion.CURRENT,
                            UUID.randomUUID(),
                            1,
                            1,
                            "worker",
                            MatchRunMode.DAILY,
                            entry.getKey(),
                            1,
                            "config",
                            new BingoScoringRules(1, List.of(1), 0, 0, 0),
                            rules,
                            List.of(
                                    new BingoTaskSpec(
                                            0, "stone", "item", Map.of("material", "STONE"))),
                            List.of(),
                            List.of());
            set(session, "manifest", manifest);
            set(session, "startedAtMillis", System.currentTimeMillis());
            set(session, "pvpEnabled", false);
            update.invoke(session);
            BossBar bar = (BossBar) field(WorkerMatchSession.class, "timerBossBar").get(session);
            assertEquals(
                    "剩余" + entry.getValue() + " • PvP保护2分10秒",
                    PlainTextComponentSerializer.plainText().serialize(bar.name()));
            assertEquals(1F, bar.progress());
            set(session, "pvpEnabled", true);
            update.invoke(session);
            assertEquals(
                    "剩余" + entry.getValue() + " • PvP已开启",
                    PlainTextComponentSerializer.plainText().serialize(bar.name()));
        }
    }

    @Test
    void formalSettlementRetainsRosterAndProtectionUntilExplicitRelease() throws Exception {
        UUID player = UUID.randomUUID(), viewer = UUID.randomUUID();
        var roster =
                List.of(
                        new PlayerSnapshot(player, "Player", ParticipantRole.PLAYER, 1),
                        new PlayerSnapshot(viewer, "Viewer", ParticipantRole.SPECTATOR, null));
        for (MatchRunMode mode : MatchRunMode.values()) {
            var session = finishedSession(waitingManifest(mode, roster));
            session.awaitCoreSettlement(true);
            assertEquals(mode.formalEvent(), session.awaitingEventSettlement());
            assertEquals(mode.formalEvent(), session.owns(player));
            assertEquals(mode.formalEvent(), session.isProtectedParticipant(player));
            assertEquals(mode.formalEvent(), session.isEventWaitingPlayer(player));
            assertEquals(mode.formalEvent(), session.isSpectator(viewer));
            assertFalse(session.isRunningPlayer(player));
            assertFalse(session.isPlaying(player));
            assertFalse(session.isEventWaitingPlayer(viewer));
            set(session, "released", true);
            assertFalse(session.owns(player));
            assertFalse(session.isSpectator(viewer));
            assertFalse(session.awaitingEventSettlement());
        }
    }

    @Test
    void eventContinuationRequiresExactPreviousMatchEpochAndPlayingRoster() throws Exception {
        var player = new PlayerSnapshot(UUID.randomUUID(), "Player", ParticipantRole.PLAYER, 1);
        var manifest = waitingManifest(MatchRunMode.EVENT, List.of(player));
        var session = finishedSession(manifest);
        session.awaitCoreSettlement(true);
        var next = waitingManifest(MatchRunMode.EVENT, List.of(player));
        var attributes = new HashMap<String, String>();
        attributes.put("coreSettlement", "true");
        attributes.put("previousMatchId", manifest.matchId().toString());
        attributes.put("previousEpoch", Long.toString(manifest.epoch()));
        var command =
                MatchMessages.command(
                        next.matchId(),
                        next.epoch(),
                        MatchCommandType.PREPARE,
                        attributes,
                        java.time.Clock.systemUTC());
        assertTrue(session.canContinueEvent(next, command));
        assertFalse(
                session.canContinueEvent(
                        waitingManifest(MatchRunMode.GAME, List.of(player)), command));
        assertFalse(
                session.canContinueEvent(waitingManifest(MatchRunMode.EVENT, List.of()), command));
        attributes.put("previousEpoch", Long.toString(manifest.epoch() + 1));
        assertFalse(
                session.canContinueEvent(
                        next,
                        MatchMessages.command(
                                next.matchId(),
                                next.epoch(),
                                MatchCommandType.PREPARE,
                                attributes,
                                java.time.Clock.systemUTC())));
        set(session, "released", true);
        assertFalse(session.canContinueEvent(next, command));
    }

    private static MatchManifest waitingManifest(MatchRunMode mode, List<PlayerSnapshot> roster) {
        var members =
                roster.stream()
                        .filter(p -> p.role() == ParticipantRole.PLAYER)
                        .map(PlayerSnapshot::uuid)
                        .toList();
        var teams =
                members.isEmpty()
                        ? List.<TeamSnapshot>of()
                        : List.of(new TeamSnapshot(1, "Red", "red", "&c", members, 0));
        return presentationManifest(mode, Map.of(), teams, roster);
    }

    private static WorkerMatchSession finishedSession(MatchManifest manifest) throws Exception {
        var session = allocate(WorkerMatchSession.class);
        var lifecycle = new MatchStateMachine();
        for (MatchState next :
                List.of(
                        MatchState.PREPARING,
                        MatchState.READY,
                        MatchState.ROUTING,
                        MatchState.COUNTDOWN,
                        MatchState.RUNNING,
                        MatchState.SETTLING,
                        MatchState.FINISHED)) lifecycle.transitionTo(next);
        set(session, "manifest", manifest);
        set(session, "lifecycle", lifecycle);
        set(
                session,
                "participants",
                manifest.participants().stream()
                        .collect(java.util.stream.Collectors.toMap(PlayerSnapshot::uuid, p -> p)));
        return session;
    }

    @Nested
    class TeammateTeleport {
        @Test
        void onlyOriginalLivePlayerSnapshotsInTheSameTeamCanTeleport() throws Exception {
            var source = new PlayerSnapshot(UUID.randomUUID(), "Source", ParticipantRole.PLAYER, 1);
            var target = new PlayerSnapshot(UUID.randomUUID(), "Target", ParticipantRole.PLAYER, 1);
            var roster = new HashMap<UUID, PlayerSnapshot>();
            roster.put(source.uuid(), source);
            roster.put(target.uuid(), target);
            var session = allocate(WorkerMatchSession.class);
            set(session, "participants", roster);
            set(
                    session,
                    "plugin",
                    proxy(
                            Plugin.class,
                            (p, m, a) -> {
                                assertEquals("isEnabled", m.getName());
                                return true;
                            }));
            var lifecycle = new MatchStateMachine();
            set(lifecycle, "state", MatchState.RUNNING);
            set(session, "lifecycle", lifecycle);
            assertTrue(teammates(session, source, target));
            for (var replacement :
                    List.of(
                            new PlayerSnapshot(
                                    target.uuid(), "Target", ParticipantRole.SPECTATOR, 1),
                            new PlayerSnapshot(target.uuid(), "Target", ParticipantRole.PLAYER, 2),
                            new PlayerSnapshot(
                                    target.uuid(), "Target", ParticipantRole.PLAYER, 1))) {
                roster.put(target.uuid(), replacement);
                assertFalse(teammates(session, source, target));
                assertEquals(
                        replacement.role() == ParticipantRole.PLAYER && replacement.teamId() == 1,
                        teammates(session, source, replacement),
                        replacement.toString());
            }
            roster.remove(target.uuid());
            assertFalse(teammates(session, source, target));
            roster.put(target.uuid(), target);
            roster.put(
                    source.uuid(),
                    new PlayerSnapshot(source.uuid(), "Source", ParticipantRole.SPECTATOR, 1));
            assertFalse(teammates(session, source, target));
            roster.put(source.uuid(), source);
            for (var state : MatchState.values())
                if (state != MatchState.RUNNING) {
                    set(lifecycle, "state", state);
                    assertFalse(teammates(session, source, target), state.name());
                }
        }

        private static boolean teammates(
                WorkerMatchSession session, PlayerSnapshot source, PlayerSnapshot target)
                throws Exception {
            var method =
                    WorkerMatchSession.class.getDeclaredMethod(
                            "currentTeammates", PlayerSnapshot.class, PlayerSnapshot.class);
            method.setAccessible(true);
            return (boolean) method.invoke(session, source, target);
        }
    }

    @Nested
    class TaskCompletion {
        @Test
        void observationsFromRemovedOrReplacedRolesCannotReachScoringOrCompletionHistory()
                throws Exception {
            var original =
                    new PlayerSnapshot(UUID.randomUUID(), "Original", ParticipantRole.PLAYER, 2);
            var session = allocate(WorkerMatchSession.class);
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
            var player =
                    new PlayerSnapshot(UUID.randomUUID(), "Original", ParticipantRole.PLAYER, 2);
            var teams = new LinkedHashMap<Integer, TeamSnapshot>();
            teams.put(1, new TeamSnapshot(1, "First", "red", "#ff0000", List.of(), 0));
            teams.put(
                    2, new TeamSnapshot(2, "Second", "blue", "#0000ff", List.of(player.uuid()), 0));
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
            var session = allocate(WorkerMatchSession.class);
            var facts = new HashMap<Integer, List<WorkerTaskCompletion>>();
            var roster = new HashMap<>(Map.of(player.uuid(), player));
            set(session, "manifest", manifest);
            set(session, "teams", lookup);
            set(session, "participants", roster);
            set(session, "completionsByCell", facts);
            var record =
                    WorkerMatchSession.class.getDeclaredMethod(
                            "recordTaskCompletion",
                            PlayerSnapshot.class,
                            CompletionObservation.class);
            record.setAccessible(true);
            record.invoke(
                    session,
                    player,
                    new CompletionObservation(
                            manifest.matchId(), 1, 1, 2, player.uuid(), 0, 147680));
            roster.clear();
            assertEquals(List.of(2, 1, 3), session.completionSnapshot().get(0));
            var frozen = session.completionSnapshot();
            assertThrows(UnsupportedOperationException.class, () -> frozen.get(0).clear());
            for (var completion : facts.get(0)) {
                assertEquals(player.uuid(), completion.playerId());
                assertEquals(
                        "Original <Second>",
                        PlainTextComponentSerializer.plainText()
                                .serialize(completion.playerName()));
                assertEquals(7384, completion.seconds());
            }
            facts.get(0).clear();
            assertEquals(List.of(2, 1, 3), frozen.get(0));
        }

        @Test
        void admissionReadsSharedScoringStateAndStillHonorsWorldOwnedLocks() throws Exception {
            var session = allocate(WorkerMatchSession.class);
            UUID match = UUID.randomUUID(), player = UUID.randomUUID();
            var rules =
                    new BingoScoringRules(
                            3,
                            List.of(30),
                            50,
                            2,
                            25,
                            new BingoVariantRules(
                                    BingoMode.POINTS, BingoDifficulty.NORMAL, 1, BingoRemix.CHAIN));
            var engine =
                    new BingoScoringEngine(
                            match,
                            1,
                            rules,
                            List.of(new TeamSnapshot(1, "team", "red", "#ff0000", List.of(player))),
                            Map.of(player, 1));
            var locks = new HashSet<Integer>();
            set(session, "scoring", engine);
            set(session, "lockedCells", locks);
            var attempt =
                    WorkerMatchSession.class.getDeclaredMethod("canAttempt", int.class, int.class);
            attempt.setAccessible(true);
            assertEquals(true, attempt.invoke(session, 1, 0));
            engine.apply(new CompletionObservation(match, 1, 1, 1, player, 0, 1));
            assertEquals(false, attempt.invoke(session, 1, 0));
            assertEquals(false, attempt.invoke(session, 1, 8));
            assertEquals(true, attempt.invoke(session, 1, 1));
            locks.add(1);
            assertEquals(false, attempt.invoke(session, 1, 1));
            assertArrayEquals(new int[] {0}, engine.completedIndices(1));
        }
    }

    /** Session wiring of {@link WorkerTestRelease} effects: entity cleanup, router and outbox. */
    @Nested
    class TestResourceRelease {
        @TempDir Path directory;

        @Test
        void disconnectInstructionCannotTargetAnUnknownParticipant() throws Exception {
            try (var f = new ReleaseFixture(directory)) {
                var rejected =
                        f.session.abort("test", Set.of(UUID.randomUUID())).toCompletableFuture();
                assertThrows(CompletionException.class, rejected::join);
                assertNull(f.retired);
                assertEquals(1L, f.published.getCount());
            }
        }

        @Test
        void retiredEntityTaskCannotReportCleanWhileItsConnectionStillExists() throws Exception {
            try (var f = new ReleaseFixture(directory)) {
                var released = f.release();
                assertFalse(released.isDone());
                f.retired.run();
                assertThrows(CompletionException.class, released::join);
                assertFalse(f.session.testResourcesReleased());
                assertEquals(1L, f.published.getCount());
            }
        }

        @Test
        void departureAndDurablePublicationBothPrecedeTheReleaseAcknowledgement() throws Exception {
            try (var f = new ReleaseFixture(directory)) {
                var released = f.release();
                f.present = false;
                f.retired.run();
                assertFalse(released.isDone());
                f.retry();
                assertTrue(f.published.await(3, TimeUnit.SECONDS));
                assertFalse(released.isDone());
                assertFalse(f.session.testResourcesReleased());
                f.receipt.complete(new DeliveryReceipt(f.event.messageId(), "events", "1-0", 1));
                released.get(3, TimeUnit.SECONDS);
                assertEquals(MatchEventType.RELEASED, f.event.type());
                assertTrue(f.session.testResourcesReleased());
                assertSame(released, f.release());
            }
        }

        @Test
        void cancelReturnRequestIsNotProofThatThePlayerLeftTheBackend() throws Exception {
            try (var f = new ReleaseFixture(directory)) {
                var left = f.router.requestAndAwaitDeparture(f.id).toCompletableFuture();
                f.router.cancel(f.id);
                f.retry();
                assertFalse(left.isDone());
                f.present = false;
                f.retry();
                assertDoesNotThrow(left::join);
            }
        }
    }

    private static final class ReleaseFixture implements AutoCloseable {
        final UUID id = UUID.randomUUID();
        final CompletableFuture<DeliveryReceipt> receipt = new CompletableFuture<>();
        final CountDownLatch published = new CountDownLatch(1);
        MatchEvent event;
        Runnable retired;
        boolean present = true;
        final WorkerMatchSession session = allocate(WorkerMatchSession.class);
        final WorkerReturnRouter router = allocate(WorkerReturnRouter.class);
        final DurableEventOutbox outbox;

        ReleaseFixture(Path directory) throws Exception {
            var entity =
                    proxy(
                            EntityScheduler.class,
                            (p, m, a) -> {
                                if (m.getName().equals("execute")) {
                                    retired = (Runnable) a[2];
                                    return true;
                                }
                                throw new AssertionError(m.getName());
                            });
            var player =
                    proxy(
                            org.bukkit.entity.Player.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getUniqueId" -> id;
                                        case "getScheduler" -> entity;
                                        case "isOnline" -> false;
                                        default -> throw new AssertionError(m.getName());
                                    });
            var global =
                    proxy(
                            GlobalRegionScheduler.class,
                            (p, m, a) -> {
                                if (m.getName().equals("execute")) {
                                    ((Runnable) a[1]).run();
                                    return null;
                                }
                                throw new AssertionError(m.getName());
                            });
            var server =
                    proxy(
                            Server.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getPlayer" -> present ? player : null;
                                        case "getGlobalRegionScheduler" -> global;
                                        default -> throw new AssertionError(m.getName());
                                    });
            var plugin =
                    proxy(
                            Plugin.class,
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "getServer" -> server;
                                        case "getLogger" ->
                                                java.util.logging.Logger.getAnonymousLogger();
                                        default -> throw new AssertionError(m.getName());
                                    });
            set(router, "plugin", plugin);
            set(router, "pending", ConcurrentHashMap.<UUID>newKeySet());
            set(router, "departures", new ConcurrentHashMap<UUID, CompletableFuture<Void>>());
            outbox =
                    new DurableEventOutbox(
                            e -> {
                                event = e;
                                published.countDown();
                                return receipt;
                            },
                            directory);
            outbox.initialize();
            var lifecycle = new MatchStateMachine();
            lifecycle.transitionTo(MatchState.ABORTED);
            set(session, "plugin", plugin);
            set(
                    session,
                    "manifest",
                    presentationManifest(MatchRunMode.TEST, Map.of(), List.of(), List.of()));
            set(session, "lifecycle", lifecycle);
            set(
                    session,
                    "participants",
                    Map.of(id, new PlayerSnapshot(id, "testbot", ParticipantRole.SPECTATOR, null)));
            set(session, "nativeScoreboardTeams", new HashMap<>());
            set(session, "scheduler", new PlatformScheduler(plugin));
            set(
                    session,
                    "config",
                    new WorkerConfig(
                            true,
                            "worker",
                            null,
                            null,
                            "BungeeCord",
                            "cc",
                            "bingo",
                            "bingo_nether",
                            "bingo_the_end",
                            true,
                            null,
                            false));
            set(session, "returnRouter", router);
            set(session, "events", outbox);
        }

        @SuppressWarnings("unchecked")
        CompletableFuture<Void> release() throws Exception {
            var method = WorkerMatchSession.class.getDeclaredMethod("releaseTestResources");
            method.setAccessible(true);
            return ((CompletionStage<Void>) method.invoke(session)).toCompletableFuture();
        }

        void retry() throws Exception {
            var m = WorkerReturnRouter.class.getDeclaredMethod("retryPending");
            m.setAccessible(true);
            m.invoke(router);
        }

        @Override
        public void close() {
            outbox.close();
        }
    }
}

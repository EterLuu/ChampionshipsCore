package ink.ziip.championshipscore.api.game.manager;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;
import java.util.concurrent.*;

class GameHotReloadTest {
    @ParameterizedTest
    @EnumSource(GameTypeEnum.class)
    void everyGameReusesIdleInstancesAndResetsActiveInstances(GameTypeEnum type) {
        var f = new Fixture();
        var idle = f.area(type, false, new Object());
        var running = f.area(type, true, new Object());
        var result = f.reload(Set.of(type), Set.of(type));
        assertEquals(1, idle.reads);
        assertEquals(0, idle.resets);
        assertEquals(1, running.resets);
        assertFalse(result.isDone());
        assertTrue(f.calls.isEmpty());
        running.reset.complete(true);
        var report = result.join();
        assertEquals(1, report.reusedInstances());
        assertEquals(1, report.resetInstances());
        assertEquals(1, report.reloadedConfigurations());
        assertEquals(0, report.failedResets());
        assertEquals(1, f.scans.get(type));
    }

    @Test
    void copiesShareOneConfigurationReadByIdentity() {
        var f = new Fixture();
        Object config = new Object();
        var a = f.area(GameTypeEnum.AceRace, false, config);
        var b = f.area(GameTypeEnum.AceRace, false, config);
        var report = f.reload(Set.of(GameTypeEnum.AceRace), Set.of(GameTypeEnum.AceRace)).join();
        assertEquals(1, a.reads + b.reads);
        assertEquals(1, report.reloadedConfigurations());
        assertEquals(2, report.reusedInstances());
    }

    @Test
    void equalButDistinctConfigurationsAreReloadedSeparately() {
        var f = new Fixture();
        f.area(GameTypeEnum.ParkourWarrior, false, new String("same"));
        f.area(GameTypeEnum.ParkourWarrior, false, new String("same"));
        assertEquals(
                2,
                f.reload(Set.of(GameTypeEnum.ParkourWarrior), Set.of(GameTypeEnum.ParkourWarrior))
                        .join()
                        .reloadedConfigurations());
    }

    @Test
    void anActiveSiblingProtectsTheSharedConfigurationRegardlessOfOrder() {
        var f = new Fixture();
        Object config = new Object();
        var idle = f.area(GameTypeEnum.AceRace, false, config);
        var active = f.area(GameTypeEnum.AceRace, true, config);
        var result = f.reload(Set.of(GameTypeEnum.AceRace), Set.of(GameTypeEnum.AceRace));
        active.reset.complete(true);
        assertEquals(0, idle.reads);
        assertEquals(0, result.join().reloadedConfigurations());
        assertEquals(1, result.join().reusedInstances());
    }

    @Test
    void disablingWaitsForEveryLocalAndRemoteOperation() {
        var f = new Fixture();
        var a = f.area(GameTypeEnum.Bingo, true, new Object());
        var b = f.area(GameTypeEnum.BattleBox, true, new Object());
        f.remoteCount = 2;
        var result =
                f.reload(
                        Set.of(GameTypeEnum.Bingo, GameTypeEnum.BattleBox),
                        Set.of(GameTypeEnum.RiptideRush));
        assertEquals(List.of("remote-stop"), f.calls);
        a.reset.complete(true);
        b.reset.complete(true);
        assertFalse(result.isDone());
        f.remoteStop.complete(null);
        assertEquals(
                List.of(
                        "remote-stop",
                        "remote-unload",
                        "disable:Bingo",
                        "disable:BattleBox",
                        "enable:RiptideRush"),
                f.calls);
        assertEquals(2, result.join().disabledManagers());
        assertEquals(1, result.join().enabledManagers());
        assertEquals(2, result.join().remoteMatchesStopped());
    }

    @Test
    void disabledManagersOpenedForEditingRemainLoaded() {
        var f = new Fixture();
        var a = f.area(GameTypeEnum.TGTTOS, false, new Object());
        var report = f.reload(Set.of(), Set.of()).join();
        assertTrue(f.loaded.contains(GameTypeEnum.TGTTOS));
        assertEquals(1, a.reads);
        assertEquals(0, report.disabledManagers());
    }

    @Test
    void idleMapsBeingDisabledAreNotReloadedOrReportedAsReused() {
        var f = new Fixture();
        var a = f.area(GameTypeEnum.SkyWars, false, new Object());
        var report = f.reload(Set.of(GameTypeEnum.SkyWars), Set.of()).join();
        assertEquals(0, a.reads);
        assertEquals(0, report.reusedInstances());
        assertEquals(1, report.disabledManagers());
    }

    @Test
    void failedConfigurationAndFalseResetAreReportedWithoutInventingSuccess() {
        var f = new Fixture();
        var a = f.area(GameTypeEnum.ParkourTag, false, new Object());
        a.readSuccess = false;
        var b = f.area(GameTypeEnum.ParkourTag, true, new Object());
        var result = f.reload(Set.of(GameTypeEnum.ParkourTag), Set.of(GameTypeEnum.ParkourTag));
        b.reset.complete(false);
        assertEquals(1, result.join().failedConfigurations());
        assertEquals(1, result.join().failedResets());
    }

    @Test
    void synchronousResetFailureStillWaitsForOtherResetsAndPerformsTheExistingReconciliation() {
        var f = new Fixture();
        var a = f.area(GameTypeEnum.BuildMart, true, new Object());
        a.throwReset = true;
        var b = f.area(GameTypeEnum.BuildMart, true, new Object());
        var result = f.reload(Set.of(GameTypeEnum.BuildMart), Set.of());
        assertFalse(result.isDone());
        assertEquals(1, b.resets);
        assertTrue(f.calls.isEmpty());
        b.reset.complete(true);
        assertThrows(CompletionException.class, result::join);
        assertEquals(List.of("disable:BuildMart"), f.calls);
    }

    @Test
    void exceptionalRemoteStopWaitsForLocalReset() {
        var f = new Fixture();
        var a = f.area(GameTypeEnum.Bingo, true, new Object());
        f.remoteCount = 1;
        var result = f.reload(Set.of(GameTypeEnum.Bingo), Set.of());
        f.remoteStop.completeExceptionally(new IllegalStateException("remote failure"));
        assertFalse(result.isDone());
        a.reset.complete(true);
        assertThrows(CompletionException.class, result::join);
    }

    @Test
    void remoteBingoLoadsOnlyAfterTheLocalManager() {
        var f = new Fixture();
        var result = f.reload(Set.of(), Set.of(GameTypeEnum.Bingo));
        assertEquals(List.of("enable:Bingo", "remote-load"), f.calls);
        assertEquals(1, result.join().enabledManagers());
    }

    @Test
    void overlappingReloadIsRejectedWithoutTouchingItsPlan() {
        var f = new Fixture();
        var a = f.area(GameTypeEnum.TNTRun, true, new Object());
        var first = f.reload(Set.of(GameTypeEnum.TNTRun), Set.of(GameTypeEnum.TNTRun));
        assertThrows(
                CompletionException.class, f.reload(Set.of(), Set.of(GameTypeEnum.Bingo))::join);
        assertEquals(1, a.resets);
        a.reset.complete(true);
        assertDoesNotThrow(first::join);
        assertTrue(f.calls.isEmpty());
    }

    @Test
    void closeDuringResetPreventsLateManagerLoadsAndCompletesTheCaller() {
        var f = new Fixture();
        var a = f.area(GameTypeEnum.LaserBox, true, new Object());
        var result = f.reload(Set.of(GameTypeEnum.LaserBox), Set.of(GameTypeEnum.Bingo));
        f.flow.close();
        assertThrows(CancellationException.class, result::join);
        assertFalse(a.reset.isDone());
        a.reset.complete(true);
        assertTrue(f.calls.isEmpty());
        assertThrows(CompletionException.class, f.reload(Set.of(), Set.of())::join);
    }

    @Test
    void closeInvalidatesAlreadyQueuedMainThreadCompletion() {
        var f = new Fixture();
        f.queued = true;
        var result = f.reload(Set.of(), Set.of(GameTypeEnum.Bingo));
        assertEquals(1, f.queue.size());
        f.flow.close();
        f.queue.remove().run();
        assertThrows(CancellationException.class, result::join);
        assertTrue(f.calls.isEmpty());
    }

    @Test
    void oldQueuedCompletionCannotFinishOrChangeANewReloadAfterReopening() {
        var f = new Fixture();
        f.queued = true;
        var old = f.reload(Set.of(), Set.of(GameTypeEnum.Bingo));
        f.flow.close();
        f.flow.open();
        var fresh = f.reload(Set.of(), Set.of(GameTypeEnum.AceRace));
        f.queue.remove().run();
        assertFalse(fresh.isDone());
        assertTrue(f.calls.isEmpty());
        f.queue.remove().run();
        assertEquals(1, fresh.join().enabledManagers());
        assertThrows(CancellationException.class, old::join);
        assertEquals(List.of("enable:AceRace"), f.calls);
    }

    @Test
    void rejectedCompletionSchedulingDoesNotLeaveReloadPermanentlyBusy() {
        var f = new Fixture();
        f.reject = true;
        assertThrows(
                CompletionException.class, f.reload(Set.of(), Set.of(GameTypeEnum.TGTTOS))::join);
        assertTrue(f.calls.isEmpty());
        f.reject = false;
        assertEquals(1, f.reload(Set.of(), Set.of(GameTypeEnum.TGTTOS)).join().enabledManagers());
    }

    @Test
    void reentrantCloseFromManagerUnloadBlocksSubsequentLoads() {
        var f = new Fixture();
        f.loaded.add(GameTypeEnum.Bingo);
        f.onDisable = f.flow::close;
        var result = f.reload(Set.of(GameTypeEnum.Bingo), Set.of(GameTypeEnum.AceRace));
        assertThrows(CancellationException.class, result::join);
        assertEquals(List.of("remote-unload", "disable:Bingo"), f.calls);
    }

    @Test
    void completionCanImmediatelyStartTheNextReload() {
        var f = new Fixture();
        var a = f.area(GameTypeEnum.Dodgebolt, true, new Object());
        var result = f.reload(Set.of(GameTypeEnum.Dodgebolt), Set.of(GameTypeEnum.Dodgebolt));
        var next =
                result.thenCompose(
                        report ->
                                f.reload(
                                        Set.of(GameTypeEnum.Dodgebolt),
                                        Set.of(GameTypeEnum.Dodgebolt, GameTypeEnum.SulfurSoccer)));
        a.active = false;
        a.reset.complete(true);
        assertEquals(1, next.join().enabledManagers());
    }

    @Test
    void failedManagerLoadIsReportedAndRetryIsPossible() {
        var f = new Fixture();
        f.failEnable = true;
        assertThrows(
                CompletionException.class, f.reload(Set.of(), Set.of(GameTypeEnum.Bingo))::join);
        assertFalse(f.calls.contains("remote-load"));
        f.failEnable = false;
        assertEquals(1, f.reload(Set.of(), Set.of(GameTypeEnum.Bingo)).join().enabledManagers());
    }

    private static final class TestArea implements GameHotReload.Area {
        final Object config;
        boolean active, readSuccess = true, throwReset;
        int reads, resets;
        final CompletableFuture<Boolean> reset = new CompletableFuture<>();

        TestArea(boolean active, Object config) {
            this.active = active;
            this.config = config;
        }

        public Object configuration() {
            return config;
        }

        public boolean active() {
            return active;
        }

        public boolean reloadConfiguration() {
            reads++;
            return readSuccess;
        }

        public CompletionStage<Boolean> reset() {
            resets++;
            if (throwReset) throw new IllegalStateException("reset");
            return reset;
        }
    }

    private static final class Fixture implements GameHotReload.Host {
        final Set<GameTypeEnum> loaded = EnumSet.noneOf(GameTypeEnum.class);
        final Map<GameTypeEnum, List<TestArea>> areas = new EnumMap<>(GameTypeEnum.class);
        final Map<GameTypeEnum, Integer> scans = new EnumMap<>(GameTypeEnum.class);
        final List<String> calls = new ArrayList<>();
        final Queue<Runnable> queue = new ArrayDeque<>();
        final CompletableFuture<Void> remoteStop = new CompletableFuture<>();
        final GameHotReload flow = new GameHotReload(this);
        int remoteCount;
        boolean queued, reject, failEnable;
        Runnable onDisable = () -> {};

        TestArea area(GameTypeEnum type, boolean active, Object config) {
            loaded.add(type);
            var a = new TestArea(active, config);
            areas.computeIfAbsent(type, k -> new ArrayList<>()).add(a);
            return a;
        }

        CompletableFuture<GameManager.ReloadReport> reload(
                Set<GameTypeEnum> old, Set<GameTypeEnum> now) {
            return flow.reload(old, now).toCompletableFuture();
        }

        public Set<GameTypeEnum> loadedGames() {
            return loaded;
        }

        public List<? extends GameHotReload.Area> instances(GameTypeEnum type) {
            scans.merge(type, 1, Integer::sum);
            return areas.getOrDefault(type, List.of());
        }

        public int activeRemoteMatches() {
            return remoteCount;
        }

        public CompletionStage<Void> stopRemoteMatches() {
            calls.add("remote-stop");
            return remoteStop;
        }

        public boolean disable(GameTypeEnum type) {
            calls.add("disable:" + type.name());
            onDisable.run();
            return loaded.remove(type);
        }

        public boolean enable(GameTypeEnum type) {
            calls.add("enable:" + type.name());
            if (failEnable) throw new IllegalStateException("load");
            return loaded.add(type);
        }

        public void disableRemoteBingo() {
            calls.add("remote-unload");
        }

        public void enableRemoteBingo() {
            if (loaded.contains(GameTypeEnum.Bingo)) calls.add("remote-load");
        }

        public void execute(Runnable action) {
            if (reject) throw new IllegalStateException("scheduler rejected");
            if (queued) queue.add(action);
            else action.run();
        }
    }
}

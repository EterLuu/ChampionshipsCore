package ink.ziip.championshipscore.api.test;

import ink.ziip.championshipscore.api.game.instance.BaseGameInstance;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;

/** Owns an entire round, including all paired copies, and keeps results in memory only. */
public final class TestEventOrchestrator {
    private final TestEventPlan plan;
    private final IntConsumer nextRound;
    private final Runnable complete;
    private final Map<UUID, Double> standings = new LinkedHashMap<>();
    private final Set<BaseGameInstance> pending = new HashSet<>();
    private int currentRound = -1;
    private boolean roundComplete = true;
    private boolean cancelled;

    TestEventOrchestrator(TestSessionManager manager, TestEventPlan plan) {
        this.plan = plan;
        nextRound = index -> manager.scheduleNextEventRound(this, index);
        complete = () -> manager.completeEvent(this);
    }

    TestEventOrchestrator(TestEventPlan plan, IntConsumer nextRound, Runnable complete) {
        this.plan = plan;
        this.nextRound = nextRound;
        this.complete = complete;
    }

    public TestEventPlan plan() {
        return plan;
    }

    public synchronized int currentRound() {
        return currentRound;
    }

    public synchronized Map<UUID, Double> standings() {
        return Map.copyOf(standings);
    }

    synchronized void markRoundStarted(int index, Collection<BaseGameInstance> instances) {
        if (cancelled
                || !roundComplete
                || index != currentRound + 1
                || index >= plan.rounds().size())
            throw new IllegalStateException("test event round order mismatch");
        if (instances.isEmpty() || instances.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("test round must own runtime instances");
        pending.clear();
        pending.addAll(instances);
        currentRound = index;
        roundComplete = false;
    }

    void onRoundComplete(int round, BaseGameInstance instance) {
        int next;
        synchronized (this) {
            if (cancelled || round != currentRound || roundComplete || !pending.remove(instance))
                return;
            instance.getPlayerPointsSnapshot()
                    .forEach((uuid, value) -> standings.merge(uuid, value, Double::sum));
            if (!pending.isEmpty()) return;
            roundComplete = true;
            next = currentRound + 1;
        }
        // Never hold the orchestrator lock while entering the manager's lifecycle lock.
        if (next >= plan.rounds().size()) complete.run();
        else nextRound.accept(next);
    }

    synchronized void cancel() {
        cancelled = true;
        pending.clear();
    }
}

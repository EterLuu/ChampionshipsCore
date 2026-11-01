package ink.ziip.championshipscore.api.test;

import java.util.Objects;
import java.util.UUID;

/** Pure lifecycle state for one Core-owned unattended test session. */
public final class TestSession {
    private final UUID sessionId;
    private final TestSessionMode mode;
    private final TestRoster roster;
    private final TestIdentityRegistry identities;
    private final TestSessionPlan plan;
    private final TestEventPlan eventPlan;
    private TestSessionState state = TestSessionState.CREATED;
    private long generation = 1;

    public TestSession(
            UUID sessionId,
            TestSessionMode mode,
            TestRoster roster,
            TestIdentityRegistry identities) {
        this(sessionId, mode, roster, identities, null);
    }

    public TestSession(
            UUID sessionId,
            TestSessionMode mode,
            TestRoster roster,
            TestIdentityRegistry identities,
            TestSessionPlan plan) {
        this(sessionId, mode, roster, identities, plan, null);
    }

    public TestSession(
            UUID sessionId,
            TestSessionMode mode,
            TestRoster roster,
            TestIdentityRegistry identities,
            TestSessionPlan plan,
            TestEventPlan eventPlan) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.roster = Objects.requireNonNull(roster, "roster");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.plan = plan;
        this.eventPlan = eventPlan;
    }

    public synchronized void beginAuthentication() {
        transition(TestSessionState.CREATED, TestSessionState.AUTHENTICATING);
    }

    public synchronized void markReady() {
        if (!roster.isComplete()) throw new IllegalStateException("test roster is incomplete");
        transition(TestSessionState.AUTHENTICATING, TestSessionState.READY);
    }

    /** A preparation roster change invalidates readiness, while retaining existing UUID leases. */
    public synchronized void resumeAuthentication() {
        if (state == TestSessionState.AUTHENTICATING) return;
        transition(TestSessionState.READY, TestSessionState.AUTHENTICATING);
    }

    public synchronized void start() {
        if (!roster.isComplete()) throw new IllegalStateException("cannot start incomplete roster");
        transition(TestSessionState.READY, TestSessionState.RUNNING);
        roster.freeze();
    }

    public synchronized void complete() {
        transition(TestSessionState.RUNNING, TestSessionState.COMPLETED);
        generation++;
    }

    public synchronized void requestStop() {
        if (state == TestSessionState.CLOSED
                || state == TestSessionState.FAILED
                || state == TestSessionState.COMPLETED)
            throw new IllegalStateException("test session is terminal");
        if (state == TestSessionState.STOPPING) return;
        state = TestSessionState.STOPPING;
        generation++;
    }

    public synchronized void fail() {
        if (state.terminal()) return;
        state = TestSessionState.FAILED;
        generation++;
    }

    public synchronized void close() {
        if (state != TestSessionState.STOPPING
                && state != TestSessionState.FAILED
                && state != TestSessionState.COMPLETED)
            throw new IllegalStateException("test session must stop before close");
        state = TestSessionState.CLOSED;
        generation++;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public TestSessionMode mode() {
        return mode;
    }

    public TestRoster roster() {
        return roster;
    }

    public TestIdentityRegistry identities() {
        return identities;
    }

    public TestSessionPlan plan() {
        return plan;
    }

    public TestEventPlan eventPlan() {
        return eventPlan;
    }

    public synchronized TestSessionState state() {
        return state;
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized boolean isCurrent(long expectedGeneration) {
        return generation == expectedGeneration && !state.terminal();
    }

    private void transition(TestSessionState expected, TestSessionState next) {
        if (state != expected)
            throw new IllegalStateException(
                    "cannot transition test session from " + state + " to " + next);
        state = next;
    }
}

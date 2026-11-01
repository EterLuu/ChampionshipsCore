package ink.ziip.championshipscore.api.test;

/** Lifecycle states owned by one test session. */
public enum TestSessionState {
    CREATED,
    AUTHENTICATING,
    READY,
    RUNNING,
    COMPLETED,
    STOPPING,
    FAILED,
    CLOSED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CLOSED;
    }
}

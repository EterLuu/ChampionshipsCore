package ink.ziip.championshipscore.protocol;

/** Durable remote-match lifecycle. SCC is authoritative for transitions between these states. */
public enum MatchState {
    CREATED,
    PREPARING,
    READY,
    ROUTING,
    COUNTDOWN,
    RUNNING,
    SETTLING,
    SUSPENDED,
    FINISHED,
    ABORTED;

    public boolean terminal() {
        return this == FINISHED || this == ABORTED;
    }
}

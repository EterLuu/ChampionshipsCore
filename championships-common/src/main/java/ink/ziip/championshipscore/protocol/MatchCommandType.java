package ink.ziip.championshipscore.protocol;

public enum MatchCommandType {
    PREPARE,
    START_COMMIT,
    ADD_SPECTATOR,
    REMOVE_SPECTATOR,
    FORCE_END,
    ABORT,
    SHUTDOWN_WHEN_IDLE
}

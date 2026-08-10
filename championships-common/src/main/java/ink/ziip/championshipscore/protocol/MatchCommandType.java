package ink.ziip.championshipscore.protocol;

public enum MatchCommandType {
    PREPARE,
    START_COMMIT,
    ADD_SPECTATOR,
    REMOVE_SPECTATOR,
    REMOVE_PARTICIPANTS,
    FORCE_END,
    ABORT,
    SHUTDOWN_WHEN_IDLE
}

package ink.ziip.championshipscore.api.object.game;

/** Identifies who owns the lifecycle around one independently runnable game instance. */
public enum GameRunMode {
    GAME,
    EVENT,
    /** Public, self-service match. Results are isolated from the championship score tables. */
    DAILY
}

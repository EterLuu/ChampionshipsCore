package ink.ziip.championshipscore.api.game.model;

/** Identifies who owns the lifecycle around one independently runnable game instance. */
public enum GameRunMode {
    GAME,
    EVENT,
    /** Core-owned test run; it may settle into the isolated test deployment's data stores. */
    TEST,
    /**
     * Core-owned event-shaped test run; keeps event gameplay semantics without formal settlement.
     */
    TEST_EVENT,
    /** Public, self-service match. Results are isolated from the championship score tables. */
    DAILY
}

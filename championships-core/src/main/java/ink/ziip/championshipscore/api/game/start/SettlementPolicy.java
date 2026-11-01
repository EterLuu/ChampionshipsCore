package ink.ziip.championshipscore.api.game.start;

/** Controls whether a game may write scores and completion events for its owning data domain. */
public enum SettlementPolicy {
    NORMAL,
    FORMAL_RANK,
    DAILY,
    /** Test deployment settlement: writes real results without owning formal Schedule state. */
    TEST
}

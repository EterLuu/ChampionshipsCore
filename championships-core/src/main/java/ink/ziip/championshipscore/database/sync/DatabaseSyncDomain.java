package ink.ziip.championshipscore.database.sync;

/** Database-backed cache families invalidated across Core instances. */
public enum DatabaseSyncDomain {
    TEAM,
    PLAYER,
    RANK
}

package ink.ziip.championshipscore.api.visibility;

/** The durable, UUID-backed rule used to decide which player entities a viewer may see. */
public enum PlayerVisibilityMode {
    ALL,
    TEAMMATES,
    SELF,
    TEAMS,
    PLAYERS
}

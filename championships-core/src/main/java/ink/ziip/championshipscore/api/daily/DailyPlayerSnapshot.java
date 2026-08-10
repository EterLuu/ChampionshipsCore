package ink.ziip.championshipscore.api.daily;

/** Immutable PAPI/sidebar view; it contains no Bukkit or mutable game objects. */
public record DailyPlayerSnapshot(String mode, String partyLeader, int partySize, String selectedGame,
                                  String queueState, int queuePlayers, int countdown,
                                  String activeGame, String activeMap, String matchId) {
    public static DailyPlayerSnapshot empty(String mode) {
        return new DailyPlayerSnapshot(mode, "-", 1, "-", "IDLE", 0, -1, "-", "-", "-");
    }
}

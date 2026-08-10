package ink.ziip.championshipscore.api.daily;

/** Immutable queue and team-shaping rules for one daily-play adapter. */
public record DailyRules(int minPlayers, int maxPlayers, int teamSize, int teams, int countdownSeconds) {
    public DailyRules {
        teamSize = Math.max(1, teamSize);
        teams = Math.max(1, teams);
        int capacity = (int) Math.min(Integer.MAX_VALUE, (long) teamSize * teams);
        maxPlayers = Math.max(1, Math.min(maxPlayers, capacity));
        minPlayers = Math.max(1, Math.min(minPlayers, maxPlayers));
        countdownSeconds = Math.max(3, countdownSeconds);
    }
}

package ink.ziip.championshipscore.api.schedule;

import ink.ziip.championshipscore.api.object.schedule.TwoVTwoVector;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import java.util.*;

/** Pairing rules shared by the formal 1v1-team games. */
public final class FormalPairingScheduler {
    private static final int MAX_ROUNDS = 9;

    private FormalPairingScheduler() {}

    public static int totalRounds(int teamCount) {
        return teamCount < 2 || teamCount % 2 != 0 ? 0 : Math.min(MAX_ROUNDS, teamCount - 1);
    }

    /** Number of rounds that are drawn before any results exist. */
    public static int seededRounds(int teamCount) {
        return (totalRounds(teamCount) + 1) / 2;
    }

    public static List<List<TwoVTwoVector>> roundRobin(List<ChampionshipTeam> teams, int count) {
        if (teams == null || teams.size() < 2 || teams.size() % 2 != 0
                || new HashSet<>(teams).size() != teams.size() || count <= 0) return List.of();
        int roundsToMake = Math.min(count, totalRounds(teams.size()));
        List<ChampionshipTeam> ring = new ArrayList<>(teams);
        List<List<TwoVTwoVector>> rounds = new ArrayList<>();
        for (int round = 0; round < roundsToMake; round++) {
            List<TwoVTwoVector> pairs = new ArrayList<>();
            for (int pair = 0; pair < ring.size() / 2; pair++)
                pairs.add(new TwoVTwoVector(ring.get(pair), ring.get(ring.size() - 1 - pair)));
            rounds.add(List.copyOf(pairs));
            ring.add(1, ring.removeLast());
        }
        return List.copyOf(rounds);
    }

    /**
     * Creates one standings round. Teams are ordered by score, then by the supplied list order.
     * A backtracking matching keeps adjacent standings together while guaranteeing no old pair is
     * used again whenever a valid matching exists.
     */
    public static List<TwoVTwoVector> standingsRound(List<ChampionshipTeam> teams,
                                                      Map<ChampionshipTeam, Double> scores,
                                                      Set<String> previousOpponents) {
        if (teams == null || teams.size() < 2 || teams.size() % 2 != 0) return List.of();
        List<ChampionshipTeam> ordered = new ArrayList<>(teams);
        Map<ChampionshipTeam, Integer> seedOrder = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) seedOrder.put(ordered.get(i), i);
        ordered.sort(Comparator
                .comparingDouble((ChampionshipTeam team) -> scores.getOrDefault(team, 0D)).reversed()
                .thenComparingInt(seedOrder::get));

        List<TwoVTwoVector> result = new ArrayList<>();
        Set<ChampionshipTeam> used = new HashSet<>();
        if (!match(ordered, scores, previousOpponents == null ? Set.of() : previousOpponents,
                used, result)) return List.of();
        return List.copyOf(result);
    }

    private static boolean match(List<ChampionshipTeam> ordered,
                                 Map<ChampionshipTeam, Double> scores,
                                 Set<String> previous,
                                 Set<ChampionshipTeam> used,
                                 List<TwoVTwoVector> result) {
        if (used.size() == ordered.size()) return true;
        ChampionshipTeam first = ordered.stream().filter(team -> !used.contains(team)).findFirst().orElse(null);
        if (first == null) return true;
        int firstIndex = ordered.indexOf(first);
        List<ChampionshipTeam> candidates = ordered.stream()
                .filter(team -> !used.contains(team) && team != first
                        && !previous.contains(opponentKey(first, team)))
                .sorted(Comparator
                        .comparingDouble((ChampionshipTeam team) ->
                                Math.abs(scores.getOrDefault(first, 0D) - scores.getOrDefault(team, 0D)))
                        .thenComparingInt(team -> Math.abs(ordered.indexOf(team) - firstIndex)))
                .toList();
        for (ChampionshipTeam second : candidates) {
            used.add(first);
            used.add(second);
            result.add(new TwoVTwoVector(first, second));
            if (match(ordered, scores, previous, used, result)) return true;
            result.removeLast();
            used.remove(first);
            used.remove(second);
        }
        return false;
    }

    public static String opponentKey(ChampionshipTeam first, ChampionshipTeam second) {
        String left = first.getName();
        String right = second.getName();
        return left.compareTo(right) <= 0 ? left + "\u0000" + right : right + "\u0000" + left;
    }

    public static void rememberOpponents(Collection<TwoVTwoVector> pairs, Set<String> target) {
        for (TwoVTwoVector pair : pairs)
            target.add(opponentKey(pair.getTeamOne(), pair.getTeamTwo()));
    }
}

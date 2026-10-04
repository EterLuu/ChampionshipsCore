package ink.ziip.championshipscore.api.schedule;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.schedule.model.TwoVTwoVector;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import org.junit.jupiter.api.Test;

import java.util.*;

class FormalPairingSchedulerTest {
    @Test
    void usesCeilingHalfAsSeededRounds() {
        assertEquals(1, FormalPairingScheduler.seededRounds(2));
        assertEquals(2, FormalPairingScheduler.seededRounds(4));
        assertEquals(3, FormalPairingScheduler.seededRounds(6));
        assertEquals(5, FormalPairingScheduler.seededRounds(10));
        assertEquals(5, FormalPairingScheduler.seededRounds(16));
    }

    @Test
    void standingsRoundsAreStrongAgainstStrongAndNeverRepeat() {
        for (int count = 2; count <= 16; count += 2) {
            List<ChampionshipTeam> teams = new ArrayList<>();
            for (int id = 0; id < count; id++) teams.add(new TestTeam(id));
            Map<ChampionshipTeam, Double> scores = new HashMap<>();
            teams.forEach(team -> scores.put(team, 0D));
            Set<String> previous = new HashSet<>();
            List<List<TwoVTwoVector>> seeded =
                    FormalPairingScheduler.roundRobin(
                            teams, FormalPairingScheduler.seededRounds(count));
            seeded.forEach(round -> FormalPairingScheduler.rememberOpponents(round, previous));
            assertEquals(FormalPairingScheduler.seededRounds(count), seeded.size());

            for (List<TwoVTwoVector> round : seeded) {
                assertEveryTeamOnce(round, teams);
                for (TwoVTwoVector pair : round) scores.merge(pair.getTeamOne(), 1D, Double::sum);
            }
            for (int round = seeded.size();
                    round < FormalPairingScheduler.totalRounds(count);
                    round++) {
                List<TwoVTwoVector> next =
                        FormalPairingScheduler.standingsRound(teams, scores, previous);
                assertFalse(next.isEmpty());
                assertEveryTeamOnce(next, teams);
                for (TwoVTwoVector pair : next) {
                    assertTrue(
                            previous.add(
                                    FormalPairingScheduler.opponentKey(
                                            pair.getTeamOne(), pair.getTeamTwo())));
                    scores.merge(pair.getTeamOne(), 1D, Double::sum);
                }
            }
        }
    }

    @Test
    void pairsAdjacentStandingsWhenNoHistoryBlocksIt() {
        List<ChampionshipTeam> teams =
                List.of(new TestTeam(1), new TestTeam(2), new TestTeam(3), new TestTeam(4));
        Map<ChampionshipTeam, Double> scores =
                Map.of(teams.get(0), 5D, teams.get(1), 5D, teams.get(2), 1D, teams.get(3), 1D);
        List<TwoVTwoVector> pairs = FormalPairingScheduler.standingsRound(teams, scores, Set.of());
        assertEquals(
                Set.of(Set.of(teams.get(0), teams.get(1)), Set.of(teams.get(2), teams.get(3))),
                pairs.stream()
                        .map(pair -> Set.of(pair.getTeamOne(), pair.getTeamTwo()))
                        .collect(java.util.stream.Collectors.toSet()));
    }

    private static void assertEveryTeamOnce(
            List<TwoVTwoVector> pairs, List<ChampionshipTeam> teams) {
        Set<ChampionshipTeam> playing = new HashSet<>();
        for (TwoVTwoVector pair : pairs) {
            assertTrue(playing.add(pair.getTeamOne()));
            assertTrue(playing.add(pair.getTeamTwo()));
        }
        assertEquals(Set.copyOf(teams), playing);
    }

    private static final class TestTeam extends ChampionshipTeam {
        TestTeam(int id) {
            super(id, "team-" + id, "red", "#FFFFFF", null);
        }
    }
}

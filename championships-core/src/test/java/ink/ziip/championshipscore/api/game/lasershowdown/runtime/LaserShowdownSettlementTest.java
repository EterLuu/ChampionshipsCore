package ink.ziip.championshipscore.api.game.lasershowdown.runtime;

import static ink.ziip.championshipscore.support.TestObjects.allocate;
import static ink.ziip.championshipscore.support.TestObjects.set;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import org.junit.jupiter.api.Test;

import java.util.*;

class LaserShowdownSettlementTest {
    @Test
    void zeroKillTeamsReceiveTheirRankAwardInEveryMode() throws Exception {
        for (GameRunMode mode : List.of(GameRunMode.EVENT, GameRunMode.GAME, GameRunMode.DAILY)) {
            var area = allocate(Area.class);
            area.awards = new HashMap<>();
            Team a = new Team(1), b = new Team(2), c = new Team(3), d = new Team(4);
            set(area, "runMode", mode);
            set(area, "gameTeams", List.of(d, b, a, c));
            set(area, "teamShootTimes", Map.of(a, 8, b, 8, c, 2));
            area.calculatePoints();
            assertEquals(
                    Map.of(a.member, 60D, b.member, 60D, c.member, 55D, d.member, 50D),
                    area.awards,
                    mode.toString());
            assertEquals(List.of(a, b, c, d), area.getRankedTeams());
        }
    }

    @Test
    void allZeroTeamsTieAtTheFirstAwardTier() throws Exception {
        var area = allocate(Area.class);
        area.awards = new HashMap<>();
        Team a = new Team(1), b = new Team(2);
        set(area, "gameTeams", List.of(a, b));
        set(area, "teamShootTimes", Map.of());
        area.calculatePoints();
        assertEquals(Map.of(a.member, 60D, b.member, 60D), area.awards);
    }

    @Test
    void lowestRankAwardStopsAtTenPoints() throws Exception {
        var area = allocate(Area.class);
        area.awards = new HashMap<>();
        List<ChampionshipTeam> teams = new ArrayList<>();
        Map<ChampionshipTeam, Integer> kills = new HashMap<>();
        for (int i = 0; i < 14; i++) {
            var team = new Team(i);
            teams.add(team);
            kills.put(team, 13 - i);
        }
        set(area, "gameTeams", teams);
        set(area, "teamShootTimes", kills);
        area.calculatePoints();
        assertEquals(14, area.awards.size());
        assertEquals(10D, area.awards.get(((Team) teams.getLast()).member));
    }

    private static class Area extends LaserShowdownTeamArea {
        Map<UUID, Double> awards;

        private Area() {
            super(null, null);
        }

        @Override
        public void addPlayerPoints(UUID id, double amount) {
            awards.merge(id, amount, Double::sum);
        }

        @Override
        public void sendMessageToAllGamePlayers(String message) {}

        @Override
        public String getTeamPointsRank() {
            return "rank";
        }

        @Override
        public void addPlayerPointsToDatabase() {}
    }

    private static class Team extends ChampionshipTeam {
        final UUID member = UUID.randomUUID();

        Team(int id) {
            super(id, "team" + id, "white", "#ffffff", null);
        }

        @Override
        public Set<UUID> getMembers() {
            return Set.of(member);
        }
    }
}

package ink.ziip.championshipscore.api.game.lasershowdown.runtime;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.model.GameRunMode;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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

    private static Field field(Class<?> type, String name) throws Exception {
        for (; type != null; type = type.getSuperclass())
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        throw new NoSuchFieldException(name);
    }

    private static void set(Object o, String n, Object v) throws Exception {
        field(o.getClass(), n).set(o, v);
    }

    private static <T> T allocate(Class<T> t) throws Exception {
        return t.cast(
                ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                        .allocateInstance(t));
    }
}

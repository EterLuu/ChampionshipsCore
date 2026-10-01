package ink.ziip.championshipscore.api.schedule.laserbox;

import ink.ziip.championshipscore.ChampionshipsCore;
import ink.ziip.championshipscore.api.game.laserbox.LaserBoxArea;
import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import ink.ziip.championshipscore.api.schedule.ScheduleManager;
import ink.ziip.championshipscore.api.team.ChampionshipTeam;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LaserBoxScheduleManagerTest {
    @Test void roundRobinPairsEveryTeamOncePerRoundWithoutRepeatingOpponents() {
        for (int count = 2; count <= 16; count += 2) {
            List<ChampionshipTeam> teams = new ArrayList<>();
            for (int id = 0; id < count; id++) teams.add(new TestTeam(id));
            var rounds = LaserBoxScheduleManager.roundPairs(teams);
            assertEquals(Math.min(9, count - 1), rounds.size());
            Set<Set<ChampionshipTeam>> matches = new HashSet<>();
            for (var round : rounds) {
                Set<ChampionshipTeam> playing = new HashSet<>();
                assertEquals(count / 2, round.size());
                for (var pair : round) {
                    assertTrue(playing.add(pair.getTeamOne()));
                    assertTrue(playing.add(pair.getTeamTwo()));
                    assertTrue(matches.add(Set.of(pair.getTeamOne(), pair.getTeamTwo())));
                }
                assertEquals(Set.copyOf(teams), playing);
            }
        }
    }

    @Test void invalidTeamCountsAndRepeatedTeamsCannotCreateASchedule() {
        var team = new TestTeam(1);
        assertTrue(LaserBoxScheduleManager.roundPairs(List.of()).isEmpty());
        assertTrue(LaserBoxScheduleManager.roundPairs(List.of(team)).isEmpty());
        assertTrue(LaserBoxScheduleManager.roundPairs(List.of(team, team)).isEmpty());
        assertTrue(LaserBoxScheduleManager.roundPairs(List.of(team, new TestTeam(2), new TestTeam(3))).isEmpty());
    }

    @Test void waitsForAllCopiesAndIgnoresDuplicateEndEventsBeforeSettling() throws Exception {
        for (int currentRound : List.of(1, 2)) {
            var plugin = allocate(ChampionshipsCore.class);
            var schedule = allocate(TestSchedule.class);
            set(ChampionshipsCore.class, plugin, "scheduleManager", schedule);
            var manager = allocate(LaserBoxScheduleManager.class);
            set(ink.ziip.championshipscore.api.BaseManager.class, manager, "plugin", plugin);
            set(LaserBoxScheduleManager.class, manager, "enabled", true);
            set(LaserBoxScheduleManager.class, manager, "subRound", currentRound);
            set(LaserBoxScheduleManager.class, manager, "rounds", List.of(List.of(), List.of()));
            var first = allocate(LaserBoxArea.class);
            var second = allocate(LaserBoxArea.class);
            Set<LaserBoxArea> active = Collections.newSetFromMap(new IdentityHashMap<>());
            active.addAll(List.of(first, second));
            set(LaserBoxScheduleManager.class, manager, "activeRoundInstances", active);

            manager.onInstanceComplete(first);
            manager.onInstanceComplete(first);
            assertEquals(0, schedule.settlements);
            manager.onInstanceComplete(second);
            assertEquals(1, schedule.settlements);
            assertEquals(currentRound == 1, schedule.hasNext);
            manager.onInstanceComplete(second);
            assertEquals(1, schedule.settlements);
        }
    }

    private static final class TestSchedule extends ScheduleManager {
        int settlements;
        boolean hasNext;
        private TestSchedule() { super(null); }
        @Override public void settleEventRound(GameTypeEnum game, boolean next, Runnable after) {
            assertEquals(GameTypeEnum.LaserBox, game);
            settlements++;
            hasNext = next;
        }
    }
    private static final class TestTeam extends ChampionshipTeam {
        TestTeam(int id) { super(id, "team-" + id, "red", "#FFFFFF", null); }
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }
    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}

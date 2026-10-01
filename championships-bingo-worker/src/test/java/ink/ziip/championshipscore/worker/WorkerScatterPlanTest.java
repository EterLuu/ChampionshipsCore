package ink.ziip.championshipscore.worker;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerScatterPlanTest {
    @Test
    void sixteenTeamsKeepTheBufferAndRemainFarApartEvenAtSearchAreaEdges() {
        for (int seed = 0; seed < 100; seed++) {
            var plan = WorkerScatterPlan.create(teamIds(16), new Random(seed));
            assertEquals(16, plan.size());
            assertBufferedAndSeparated(plan, 1_900);
            assertTrue(plan.values().stream().anyMatch(area -> area.minX() == -5_000));
            assertTrue(plan.values().stream().anyMatch(area -> area.maxX() == 4_999));
            assertTrue(plan.values().stream().anyMatch(area -> area.minZ() == -5_000));
            assertTrue(plan.values().stream().anyMatch(area -> area.maxZ() == 4_999));
        }
    }

    @Test
    void sixtyFourTeamsStillHaveSeparateRegions() {
        assertBufferedAndSeparated(WorkerScatterPlan.create(teamIds(64), new Random(20)), 700);
    }

    @Test
    void expandedSearchAreasKeepTeamsInSeparateTerritories() {
        for (int count : List.of(2, 3, 4, 8, 16, 17, 64)) {
            var plan = WorkerScatterPlan.create(teamIds(count), new Random(count));
            var expanded = plan.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    entry -> WorkerScatterPlan.expand(entry.getValue(), count)));
            assertBufferedAndSeparated(expanded, count == 64 ? 650 : 1_024);
            plan.forEach((team, preferred) -> {
                var area = expanded.get(team);
                assertTrue(area.minX() <= preferred.minX() && area.maxX() >= preferred.maxX());
                assertTrue(area.minZ() <= preferred.minZ() && area.maxZ() >= preferred.maxZ());
            });
        }
    }

    @Test
    void twoTeamsUseOppositeCornersAndIncompleteGridsCoverBothAxes() {
        for (int count : List.of(2, 3, 5, 7, 10, 15, 17)) {
            var plan = WorkerScatterPlan.create(teamIds(count), new Random(count));
            assertEquals(count, plan.size());
            assertBufferedAndSeparated(plan, 1_200);
            assertTrue(plan.values().stream().anyMatch(area -> area.minX() < -3_000));
            assertTrue(plan.values().stream().anyMatch(area -> area.maxX() > 3_000));
            assertTrue(plan.values().stream().anyMatch(area -> area.minZ() < -3_000));
            assertTrue(plan.values().stream().anyMatch(area -> area.maxZ() > 3_000));
        }
        var areas = WorkerScatterPlan.create(teamIds(2), new Random(0)).values().stream().toList();
        assertTrue(minimumDistance(areas.get(0), areas.get(1)) > 11_000);
    }

    @Test
    void randomizesTeamAssignmentsAndHandlesEmptySoloAndDuplicateTeams() {
        assertNotEquals(WorkerScatterPlan.create(teamIds(16), new Random(1)),
                WorkerScatterPlan.create(teamIds(16), new Random(2)));
        assertEquals(Map.of(), WorkerScatterPlan.create(List.of(), new Random(0)));
        assertEquals(Map.of(42, new WorkerScatterPlan.SearchArea(-5_000, 4_999, -5_000, 4_999)),
                WorkerScatterPlan.create(List.of(42, 42), new Random(0)));
        assertEquals(Set.of(-1, 42, 100),
                WorkerScatterPlan.create(List.of(42, -1, 100, 42), new Random(0)).keySet());
    }

    private static void assertBufferedAndSeparated(Map<Integer, WorkerScatterPlan.SearchArea> plan, int distance) {
        Set<WorkerScatterPlan.SearchArea> unique = new HashSet<>(plan.values());
        assertEquals(plan.size(), unique.size());
        List<WorkerScatterPlan.SearchArea> areas = List.copyOf(plan.values());
        for (int index = 0; index < areas.size(); index++) {
            var area = areas.get(index);
            assertTrue(area.minX() >= -5_000 && area.maxX() < 5_000);
            assertTrue(area.minZ() >= -5_000 && area.maxZ() < 5_000);
            for (int other = index + 1; other < areas.size(); other++) {
                assertTrue(minimumDistance(area, areas.get(other)) >= distance);
                assertTrue(Math.floorDiv(area.maxX(), 512) < Math.floorDiv(areas.get(other).minX(), 512)
                        || Math.floorDiv(areas.get(other).maxX(), 512) < Math.floorDiv(area.minX(), 512)
                        || Math.floorDiv(area.maxZ(), 512) < Math.floorDiv(areas.get(other).minZ(), 512)
                        || Math.floorDiv(areas.get(other).maxZ(), 512) < Math.floorDiv(area.minZ(), 512));
            }
        }
    }

    private static double minimumDistance(WorkerScatterPlan.SearchArea first, WorkerScatterPlan.SearchArea second) {
        int dx = Math.max(0, Math.max(first.minX() - second.maxX(), second.minX() - first.maxX()));
        int dz = Math.max(0, Math.max(first.minZ() - second.maxZ(), second.minZ() - first.maxZ()));
        return Math.hypot(dx, dz);
    }

    private static List<Integer> teamIds(int count) {
        return IntStream.range(0, count).boxed().toList();
    }
}

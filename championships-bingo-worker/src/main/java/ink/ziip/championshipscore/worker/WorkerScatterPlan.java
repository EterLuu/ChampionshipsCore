package ink.ziip.championshipscore.worker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Random team assignments to widely separated search areas inside the worker's world border. */
final class WorkerScatterPlan {
    static final int WORLD_SIZE = 16_000;
    static final int BORDER_BUFFER = 3_000;
    static final int MIN_COORDINATE = -WORLD_SIZE / 2 + BORDER_BUFFER;
    static final int MAX_COORDINATE = WORLD_SIZE / 2 - BORDER_BUFFER - 1;
    private static final int MAX_SEARCH_RADIUS = 512;
    private static final int MAX_TERRITORY_GAP = 1_024;

    private WorkerScatterPlan() {}

    static Map<Integer, SearchArea> create(Collection<Integer> teamIds, Random random) {
        List<Integer> teams = new ArrayList<>(teamIds.stream().distinct().sorted().toList());
        if (teams.isEmpty()) return Map.of();
        if (teams.size() == 1)
            return Map.of(
                    teams.getFirst(),
                    new SearchArea(MIN_COORDINATE, MAX_COORDINATE, MIN_COORDINATE, MAX_COORDINATE));

        int side = (int) Math.ceil(Math.sqrt(teams.size()));
        int radius = Math.min(MAX_SEARCH_RADIUS, (MAX_COORDINATE - MIN_COORDINATE) / (4 * side));
        double low = MIN_COORDINATE + radius;
        double high = MAX_COORDINATE - radius;
        List<SearchArea> candidates = new ArrayList<>();
        for (int row = 0; row < side; row++) {
            for (int column = 0; column < side; column++) {
                int x = (int) Math.round(low + (high - low) * column / (side - 1));
                int z = (int) Math.round(low + (high - low) * row / (side - 1));
                candidates.add(new SearchArea(x - radius, x + radius, z - radius, z + radius));
            }
        }
        // Randomize ties, then repeatedly select the area farthest from the closest selected area.
        // Incomplete grids therefore spread across the whole map instead of filling adjacent rows.
        Collections.shuffle(candidates, random);
        List<SearchArea> selected = new ArrayList<>();
        selected.add(candidates.removeLast());
        while (selected.size() < teams.size()) {
            SearchArea farthest = null;
            double greatestDistance = -1;
            for (SearchArea candidate : candidates) {
                double nearest =
                        selected.stream()
                                .mapToDouble(candidate::distanceSquared)
                                .min()
                                .orElseThrow();
                if (nearest > greatestDistance) {
                    greatestDistance = nearest;
                    farthest = candidate;
                }
            }
            selected.add(farthest);
            candidates.remove(farthest);
        }
        Collections.shuffle(teams, random);
        Map<Integer, SearchArea> result = new LinkedHashMap<>();
        for (int index = 0; index < teams.size(); index++)
            result.put(teams.get(index), selected.get(index));
        return Collections.unmodifiableMap(result);
    }

    /**
     * A larger, still isolated search area for ocean or hazardous terrain near the preferred spot.
     */
    static SearchArea expand(SearchArea area, int teamCount) {
        if (teamCount <= 1) return area;
        int side = (int) Math.ceil(Math.sqrt(teamCount));
        int radius = Math.min(MAX_SEARCH_RADIUS, (MAX_COORDINATE - MIN_COORDINATE) / (4 * side));
        double spacing = (MAX_COORDINATE - MIN_COORDINATE - 2.0 * radius) / (side - 1);
        double gap = Math.min(MAX_TERRITORY_GAP, Math.floor(spacing / 2));
        double halfWidth = (spacing - gap) / 2;
        double x = (area.minX() + area.maxX()) / 2.0;
        double z = (area.minZ() + area.maxZ()) / 2.0;
        return new SearchArea(
                Math.max(MIN_COORDINATE, (int) Math.ceil(x - halfWidth)),
                Math.min(MAX_COORDINATE, (int) Math.floor(x + halfWidth)),
                Math.max(MIN_COORDINATE, (int) Math.ceil(z - halfWidth)),
                Math.min(MAX_COORDINATE, (int) Math.floor(z + halfWidth)));
    }

    record SearchArea(int minX, int maxX, int minZ, int maxZ) {
        private double distanceSquared(SearchArea other) {
            double dx = (minX + maxX - other.minX - other.maxX) / 2.0;
            double dz = (minZ + maxZ - other.minZ - other.maxZ) / 2.0;
            return dx * dx + dz * dz;
        }
    }
}

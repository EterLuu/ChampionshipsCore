package ink.ziip.championshipscore.api.game.acerace.model;

import java.util.*;

/** Owns ordered race progress and reuses placement ranges until progress or the roster changes. */
public final class AceRaceProgress {
    public record PlacementRange(int first, int last) {}

    private record Progress(int laps, int nextPoint) {}

    private static final Progress START = new Progress(0, 0);
    private static final PlacementRange ABSENT = new PlacementRange(0, 0);
    private final Map<UUID, Progress> racers = new HashMap<>();
    private final Map<UUID, Integer> finishPlaces = new LinkedHashMap<>();
    private List<UUID> finishedOrder = List.of();
    private List<UUID> cachedRoster = List.of();
    private int cachedPointCount;
    private Map<UUID, PlacementRange> cachedPlacements;

    public void reset() {
        racers.clear();
        finishPlaces.clear();
        finishedOrder = List.of();
        cachedRoster = List.of();
        cachedPlacements = null;
    }

    public int nextPoint(UUID player) {
        return racers.getOrDefault(player, START).nextPoint();
    }

    public int completedLaps(UUID player) {
        return racers.getOrDefault(player, START).laps();
    }

    public void reachPoint(UUID player, int nextPoint) {
        Progress current = racers.getOrDefault(player, START);
        update(player, new Progress(current.laps(), nextPoint));
    }

    public int completeLap(UUID player) {
        Progress current = racers.getOrDefault(player, START);
        int laps = current.laps() + 1;
        update(player, new Progress(laps, current.nextPoint()));
        return laps;
    }

    private void update(UUID player, Progress progress) {
        if (!progress.equals(racers.put(player, progress))) cachedPlacements = null;
    }

    public boolean isFinished(UUID player) {
        return finishPlaces.containsKey(player);
    }

    /** Returns the finish position, or zero when this player already finished. */
    public int finish(UUID player) {
        if (isFinished(player)) return 0;
        int place = finishPlaces.size() + 1;
        finishPlaces.put(player, place);
        finishedOrder = List.copyOf(finishPlaces.keySet());
        cachedPlacements = null;
        return place;
    }

    public List<UUID> finishedPlayers() {
        return finishedOrder;
    }

    public PlacementRange placement(UUID player, List<UUID> roster, int pointCount) {
        return placements(roster, pointCount).getOrDefault(player, ABSENT);
    }

    /** The roster is compared by value because DAILY can remove participants during a run. */
    public Map<UUID, PlacementRange> placements(List<UUID> roster, int pointCount) {
        if (cachedPlacements != null
                && cachedPointCount == pointCount
                && cachedRoster.equals(roster)) return cachedPlacements;
        Map<UUID, PlacementRange> result = new HashMap<>();
        Map<Integer, List<UUID>> groups = new TreeMap<>(Comparator.reverseOrder());
        for (UUID player : roster) {
            Integer place = finishPlaces.get(player);
            if (place != null) result.put(player, new PlacementRange(place, place));
            else {
                Progress progress = racers.getOrDefault(player, START);
                int reached = progress.laps() * pointCount + progress.nextPoint();
                groups.computeIfAbsent(reached, ignored -> new ArrayList<>()).add(player);
            }
        }
        // Finish positions remain fixed even when a DAILY roster no longer contains a finisher.
        int ahead = finishPlaces.size();
        for (List<UUID> group : groups.values()) {
            PlacementRange range = new PlacementRange(ahead + 1, ahead + group.size());
            for (UUID player : group) result.put(player, range);
            ahead += group.size();
        }
        cachedRoster = List.copyOf(roster);
        cachedPointCount = pointCount;
        cachedPlacements = Map.copyOf(result);
        return cachedPlacements;
    }
}

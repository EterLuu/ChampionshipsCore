package ink.ziip.championshipscore.api.game.acerace.model;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A fully loaded course; each recovery marker carries its own source row and gate binding. */
public record AceRaceCourse(
        List<AceRaceProgressPoint> progressPoints, List<RespawnMarker> respawns) {
    public static final AceRaceCourse EMPTY = new AceRaceCourse(List.of(), List.of());

    public AceRaceCourse {
        progressPoints = List.copyOf(progressPoints);
        respawns = List.copyOf(respawns);
        Set<Integer> sourceRows = new HashSet<>();
        for (RespawnMarker marker : respawns) {
            if (marker.binding() >= progressPoints.size())
                throw new IllegalArgumentException("Respawn binding exceeds course gates");
            if (!sourceRows.add(marker.configIndex()))
                throw new IllegalArgumentException("Duplicate respawn configuration row");
        }
    }

    public int loadedIndexForConfig(int configIndex) {
        for (int index = 0; index < respawns.size(); index++) {
            if (respawns.get(index).configIndex() == configIndex) return index;
        }
        return -1;
    }

    public int bindingForConfig(int configIndex) {
        int index = loadedIndexForConfig(configIndex);
        return index < 0 ? -1 : respawns.get(index).binding();
    }

    /** Ordered by actual travel, not configuration order; already captured markers are omitted. */
    public List<Integer> crossedRespawns(
            Location previous, Location current, Set<Integer> captured) {
        List<Crossing> crossings = new ArrayList<>();
        for (int index = 0; index < respawns.size(); index++) {
            if (captured.contains(index)) continue;
            double progress = respawns.get(index).point().crossingProgress(previous, current);
            if (progress >= 0D) crossings.add(new Crossing(index, progress));
        }
        crossings.sort(Comparator.comparingDouble(Crossing::progress));
        return crossings.stream().map(Crossing::index).toList();
    }

    public record RespawnMarker(AceRaceRespawnPoint point, int configIndex, int binding) {
        public RespawnMarker {
            Objects.requireNonNull(point);
            if (configIndex < 0 || binding < -1)
                throw new IllegalArgumentException("Invalid respawn row or binding");
        }
    }

    private record Crossing(int index, double progress) {}
}

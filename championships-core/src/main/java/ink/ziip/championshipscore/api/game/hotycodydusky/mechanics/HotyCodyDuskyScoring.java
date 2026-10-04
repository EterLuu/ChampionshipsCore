package ink.ziip.championshipscore.api.game.hotycodydusky.mechanics;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One whole-instance ranking, retaining Hoty Cody Dusky's existing dense tie ranks and awards. */
public final class HotyCodyDuskyScoring {
    private HotyCodyDuskyScoring() {}

    public static Map<UUID, Integer> rankingBonuses(
            Map<UUID, Long> deathTimes, Collection<UUID> survivors, long endTime) {
        Map<UUID, Long> times = new HashMap<>(deathTimes);
        survivors.forEach(id -> times.put(id, endTime));
        Map<UUID, Integer> awards = new LinkedHashMap<>();
        int rank = 0;
        Long previous = null;
        for (var entry :
                times.entrySet().stream()
                        .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                        .toList()) {
            if (!entry.getValue().equals(previous)) {
                rank++;
                previous = entry.getValue();
            }
            int value =
                    switch (rank) {
                        case 1 -> 25;
                        case 2 -> 20;
                        case 3 -> 15;
                        default -> 0;
                    };
            if (value > 0) awards.put(entry.getKey(), value);
        }
        return Map.copyOf(awards);
    }
}

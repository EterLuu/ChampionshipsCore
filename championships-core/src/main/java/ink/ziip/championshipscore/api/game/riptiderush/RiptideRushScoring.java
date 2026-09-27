package ink.ziip.championshipscore.api.game.riptiderush;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Competition ranks: tied players share an award and occupy their combined number of places. */
public final class RiptideRushScoring {
    private static final int[] PODIUM = {100, 70, 30};

    private RiptideRushScoring() {
    }

    public static @NotNull Map<UUID, Integer> placementBonuses(
            @NotNull Collection<UUID> survivors, @NotNull List<? extends Collection<UUID>> eliminationGroups) {
        Map<UUID, Integer> result = new LinkedHashMap<>();
        survivors.forEach(uuid -> result.put(uuid, PODIUM[0]));
        int occupied = survivors.size();
        for (int index = eliminationGroups.size() - 1; index >= 0 && occupied < PODIUM.length; index--) {
            Collection<UUID> group = eliminationGroups.get(index);
            int bonus = PODIUM[occupied];
            group.forEach(uuid -> result.put(uuid, bonus));
            occupied += group.size();
        }
        return Map.copyOf(result);
    }
}

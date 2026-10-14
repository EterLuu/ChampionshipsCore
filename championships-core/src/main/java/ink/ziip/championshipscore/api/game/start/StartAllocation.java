package ink.ziip.championshipscore.api.game.start;

import ink.ziip.championshipscore.configuration.config.message.MessageConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure allocation shared by start preflight and single-instance multi-arena gameplay. */
public final class StartAllocation {
    public record Pair<T>(T first, T second) {}

    private StartAllocation() {}

    public static <T> List<Pair<T>> pair(List<T> teams) {
        if (teams.size() < 2
                || teams.size() % 2 != 0
                || new HashSet<>(teams).size() != teams.size()) {
            throw new IllegalArgumentException(MessageConfig.codeText("start-allocation.pair-1"));
        }
        List<Pair<T>> result = new ArrayList<>();
        for (int i = 0; i < teams.size(); i += 2)
            result.add(new Pair<>(teams.get(i), teams.get(i + 1)));
        return List.copyOf(result);
    }

    /**
     * Team members rotate over the selected physical indices while preserving the complete roster.
     */
    public static Map<UUID, Integer> spread(List<List<UUID>> teams, List<Integer> arenas) {
        if (arenas.isEmpty() || new HashSet<>(arenas).size() != arenas.size()) {
            throw new IllegalArgumentException(MessageConfig.codeText("start-allocation.spread-1"));
        }
        Map<UUID, Integer> result = new LinkedHashMap<>();
        for (int team = 0; team < teams.size(); team++) {
            List<UUID> members = teams.get(team);
            for (int member = 0; member < members.size(); member++) {
                UUID id = members.get(member);
                if (id == null
                        || result.put(id, arenas.get((team + member) % arenas.size())) != null) {
                    throw new IllegalArgumentException(
                            MessageConfig.codeText("start-allocation.spread-2"));
                }
            }
        }
        return Map.copyOf(result);
    }
}

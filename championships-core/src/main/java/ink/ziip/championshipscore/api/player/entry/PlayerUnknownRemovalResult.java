package ink.ziip.championshipscore.api.player.entry;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Result of removing every ChampionshipsCore identity outside the authoritative allowlist. */
public record PlayerUnknownRemovalResult(
        int examinedUuids,
        @NotNull Set<UUID> removedUuids,
        @NotNull Map<String, Integer> removedRowsByTable
) {
    public PlayerUnknownRemovalResult {
        examinedUuids = Math.max(0, examinedUuids);
        removedUuids = Set.copyOf(removedUuids);
        Map<String, Integer> defensive = new LinkedHashMap<>();
        removedRowsByTable.forEach((table, rows) -> {
            if (table == null || table.isBlank()) throw new IllegalArgumentException("Table name is required");
            defensive.put(table, Math.max(0, rows));
        });
        removedRowsByTable = Map.copyOf(defensive);
    }

    public int removedRows() {
        return removedRowsByTable.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }
}

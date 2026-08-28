package ink.ziip.championshipscore.api.player.entry;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerUnknownRemovalResultTest {
    @Test
    void copiesResultCollectionsAndSumsRows() {
        UUID removed = UUID.randomUUID();
        Set<UUID> removedUuids = new java.util.HashSet<>(Set.of(removed));
        Map<String, Integer> rows = new LinkedHashMap<>(Map.of("players", 1, "player_points", 3));

        PlayerUnknownRemovalResult result = new PlayerUnknownRemovalResult(3, removedUuids, rows);
        removedUuids.clear();
        rows.clear();

        assertEquals(Set.of(removed), result.removedUuids());
        assertEquals(4, result.removedRows());
        assertEquals(1, result.removedRowsByTable().get("players"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.removedRowsByTable().put("teams", 1));
    }

    @Test
    void rejectsInvalidTableNames() {
        Map<String, Integer> rows = new LinkedHashMap<>();
        rows.put(null, 1);
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerUnknownRemovalResult(1, Set.of(), rows));
    }
}

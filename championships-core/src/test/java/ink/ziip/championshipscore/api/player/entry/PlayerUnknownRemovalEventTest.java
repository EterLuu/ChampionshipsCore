package ink.ziip.championshipscore.api.player.entry;

import ink.ziip.championshipscore.api.player.event.PlayerUnknownRemovalEvent;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlayerUnknownRemovalEventTest {
    @Test
    void copiesAllowlistAndDoesNotTrackLaterMutation() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Set<UUID> allowed = new TreeSet<>(Set.of(first, second));

        PlayerUnknownRemovalEvent event = new PlayerUnknownRemovalEvent(allowed);
        allowed.clear();

        assertEquals(Set.of(first, second), event.getAllowedUuids());
        assertFalse(event.completion().isDone());
    }
}

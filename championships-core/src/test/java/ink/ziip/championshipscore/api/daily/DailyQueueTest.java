package ink.ziip.championshipscore.api.daily;

import ink.ziip.championshipscore.api.object.game.GameTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyQueueTest {
    private final DailyRules rules = new DailyRules(2, 8, 4, 2, 5);

    @Test
    void partyIsAlwaysRemovedAndRestoredAtomically() {
        DailyQueue queue = new DailyQueue(GameTypeEnum.Bingo);
        UUID group = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(queue.add(group, Set.of(first, second), rules));

        assertEquals(Set.of(first, second), queue.removePlayer(first));
        assertEquals(0, queue.size());

        queue.restore(List.of(new DailyQueue.Group(group, new java.util.LinkedHashSet<>(Set.of(first, second)))), rules);
        assertEquals(2, queue.size());
    }

    @Test
    void rejectsPartyLargerThanOneTeam() {
        DailyQueue queue = new DailyQueue(GameTypeEnum.AceRace);
        Set<UUID> players = Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());

        assertFalse(queue.canAdd(players, rules));
    }

    @Test
    void keepsOverflowQueuedForAnotherRuntimeInstance() {
        DailyQueue queue = new DailyQueue(GameTypeEnum.AceRace);
        for (int index = 0; index < 4; index++) {
            UUID player = UUID.randomUUID();
            assertTrue(queue.add(player, Set.of(player), rules));
        }

        List<DailyQueue.Group> firstMatch = queue.take(2);
        List<DailyQueue.Group> secondMatch = queue.take(2);

        assertEquals(2, firstMatch.size());
        assertEquals(2, secondMatch.size());
        assertEquals(0, queue.size());
    }
}

package ink.ziip.championshipscore.api.game.start;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class StartAllocationTest {
    @Test
    void eachTeamIsUsedInOnePairWithoutAnOddTeamBeingDiscarded() {
        var pairs = StartAllocation.pair(List.of("a", "b", "c", "d", "e", "f"));
        assertEquals(
                List.of(
                        new StartAllocation.Pair<>("a", "b"),
                        new StartAllocation.Pair<>("c", "d"),
                        new StartAllocation.Pair<>("e", "f")),
                pairs);
        assertThrows(
                IllegalArgumentException.class, () -> StartAllocation.pair(List.of("a", "b", "c")));
        assertThrows(IllegalArgumentException.class, () -> StartAllocation.pair(List.of("a", "a")));
    }

    @Test
    void allMembersCanPlayInOneSelectedArenaWithoutChangingTheRoster() {
        var teams =
                List.of(
                        List.of(UUID.randomUUID(), UUID.randomUUID()),
                        List.of(UUID.randomUUID(), UUID.randomUUID()));
        var allocation = StartAllocation.spread(teams, List.of(3));
        assertEquals(4, allocation.size());
        assertTrue(allocation.values().stream().allMatch(index -> index == 3));
    }

    @Test
    void automaticSpreadUsesPhysicalArenaIdsAndBalancesEachTeam() {
        var teams =
                List.of(
                        List.of(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID()),
                        List.of(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                UUID.randomUUID()));
        var allocation = StartAllocation.spread(teams, List.of(0, 2));
        assertEquals(8, allocation.size());
        for (var team : teams) {
            assertEquals(2, team.stream().filter(id -> allocation.get(id) == 0).count());
            assertEquals(2, team.stream().filter(id -> allocation.get(id) == 2).count());
        }
    }

    @Test
    void duplicatePlayersAndEmptyArenaSelectionCannotProduceAPartialRoster() {
        var id = UUID.randomUUID();
        assertThrows(
                IllegalArgumentException.class,
                () -> StartAllocation.spread(List.of(List.of(id), List.of(id)), List.of(0, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> StartAllocation.spread(List.of(List.of(id)), List.of()));
    }
}

package ink.ziip.championshipscore.api.vote;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.finale.FinaleGameRegistry;
import ink.ziip.championshipscore.api.game.model.GameTypeEnum;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

class VoteMenuTest {
    private static Set<Integer> reserved() {
        var slots = new HashSet<Integer>();
        IntStream.range(0, 9).forEach(slots::add);
        IntStream.range(45, 54).forEach(slots::add);
        slots.add(40);
        return slots;
    }

    @Test
    void oldTwelveSlotConfigurationShowsEveryScoringGame() {
        int count =
                (int)
                        List.of(GameTypeEnum.values()).stream()
                                .filter(game -> !FinaleGameRegistry.isRegistered(game))
                                .count();
        assertEquals(14, count);
        var slots =
                VoteMenu.ballotSlots(
                        List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23), reserved(), count);
        assertEquals(count, slots.size());
        assertEquals(count, new HashSet<>(slots).size());
        assertEquals(List.of(24, 25), slots.subList(12, 14));
        assertTrue(slots.stream().noneMatch(reserved()::contains));
    }

    @Test
    void overlappingCustomPositionsCannotOverwriteControlsOrHideGames() {
        var blocked = reserved();
        blocked.add(24);
        var slots = VoteMenu.ballotSlots(List.of(-1, 54, 1, 4, 7, 40, 49, 20, 20), blocked, 14);
        assertEquals(14, slots.size());
        assertEquals(20, slots.getFirst());
        assertEquals(14, new HashSet<>(slots).size());
        assertTrue(slots.stream().noneMatch(blocked::contains));
    }

    @Test
    void emptyBallotAndAdditionalRowsRemainValid() {
        assertEquals(List.of(), VoteMenu.ballotSlots(List.of(), reserved(), 0));
        var slots = VoteMenu.ballotSlots(List.of(), reserved(), 21);
        assertEquals(21, slots.size());
        assertTrue(slots.stream().allMatch(slot -> slot >= 9 && slot < 45));
        assertThrows(
                IllegalStateException.class,
                () ->
                        VoteMenu.ballotSlots(
                                List.of(),
                                IntStream.range(0, 54)
                                        .boxed()
                                        .collect(java.util.stream.Collectors.toSet()),
                                14));
    }
}

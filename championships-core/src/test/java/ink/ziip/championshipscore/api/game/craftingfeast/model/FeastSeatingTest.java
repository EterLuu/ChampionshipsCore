package ink.ziip.championshipscore.api.game.craftingfeast.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class FeastSeatingTest {
    // Seats 0..5 are in the main hall, 6..8 in a private room.
    private static final List<Boolean> SEATS =
            List.of(false, false, false, false, false, false, true, true, true);

    @Test
    void teachingUsesClosestFreePreferredSeatWithTheNormalOverflowRule() {
        double[] distances = {10, 9, 8, 7, 6, 5, 3, 2, 1};
        assertEquals(5, FeastSeating.nearest(SEATS, Set.of(), false, i -> distances[i]));
        assertEquals(8, FeastSeating.nearest(SEATS, Set.of(), true, i -> distances[i]));
        assertEquals(4, FeastSeating.nearest(SEATS, Set.of(5), false, i -> distances[i]));
        assertEquals(5, FeastSeating.nearest(SEATS, Set.of(6, 7, 8), true, i -> distances[i]));
        assertEquals(
                -1,
                FeastSeating.nearest(
                        SEATS, Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8), false, i -> distances[i]));
    }

    @Test
    void vipsTakePrivateRoomsAndOthersTheHallWhileFree() {
        Random random = new Random(1);
        for (int i = 0; i < 200; i++) {
            assertTrue(FeastSeating.choose(SEATS, Set.of(), true, random) >= 6);
            assertTrue(FeastSeating.choose(SEATS, Set.of(), false, random) < 6);
        }
    }

    @Test
    void fullAreasOverflowIntoTheOtherAndNothingFreeReturnsMinusOne() {
        Random random = new Random(2);
        assertEquals(1, FeastSeating.choose(SEATS, Set.of(0, 2, 3, 4, 5, 6, 7, 8), true, random));
        assertEquals(7, FeastSeating.choose(SEATS, Set.of(0, 1, 2, 3, 4, 5, 6, 8), false, random));
        Set<Integer> all = new HashSet<>();
        for (int seat = 0; seat < SEATS.size(); seat++) all.add(seat);
        assertEquals(-1, FeastSeating.choose(SEATS, all, false, random));
    }

    @Test
    void seatsAreSpreadRandomlyOverEveryFreeChair() {
        Random random = new Random(3);
        Map<Integer, Integer> hits = new HashMap<>();
        for (int i = 0; i < 6000; i++)
            hits.merge(FeastSeating.choose(SEATS, Set.of(2), false, random), 1, Integer::sum);
        assertEquals(Set.of(0, 1, 3, 4, 5), hits.keySet(), "taken seats are never chosen");
        hits.values().forEach(count -> assertTrue(count > 1000 && count < 1400, hits.toString()));
    }
}

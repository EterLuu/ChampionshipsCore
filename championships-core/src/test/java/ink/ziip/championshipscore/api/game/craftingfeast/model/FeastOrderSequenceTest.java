package ink.ziip.championshipscore.api.game.craftingfeast.model;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.IntStream;

class FeastOrderSequenceTest {
    private static final List<FeastRequest> MENU =
            List.of(
                    new FeastRequest("bread", "面包", Material.BREAD, 1, 100),
                    new FeastRequest("cod", "熟鳕鱼", Material.COOKED_COD, 1, 100),
                    new FeastRequest("glass", "玻璃", Material.GLASS, 1, 100),
                    new FeastRequest("cookie", "曲奇", Material.COOKIE, 4, 120));
    private static final FeastGuestProfile FOX =
            new FeastGuestProfile(
                    "fox",
                    "狐狸",
                    "FOX",
                    50,
                    List.of(new FeastRequest("stew", "兔肉煲", Material.RABBIT_STEW, 1, 240)));

    @Test
    void everyRestaurantDrawsTheSameGuestRegardlessOfDrawOrder() {
        FeastOrderSequence forward =
                new FeastOrderSequence(MENU, 7L, 5, List.of(FOX), 20, List.of());
        FeastOrderSequence backward =
                new FeastOrderSequence(MENU, 7L, 5, List.of(FOX), 20, List.of());
        List<FeastGuest> first = IntStream.range(0, 60).mapToObj(forward::guest).toList();
        List<FeastGuest> second = new ArrayList<>();
        for (int i = 59; i >= 0; i--) second.addFirst(backward.guest(i));
        assertEquals(first, second);
        assertEquals(
                IntStream.rangeClosed(1, 60).boxed().toList(),
                first.stream().map(FeastGuest::ticket).toList());
    }

    @Test
    void ordinaryGuestsServeTheWholeMenuEachRoundWithoutBackToBackRepeats() {
        for (long seed = 0; seed < 50; seed++) {
            FeastOrderSequence sequence = new FeastOrderSequence(MENU, seed, 0);
            for (int round = 0; round < 10; round++) {
                Set<FeastRequest> served = new HashSet<>();
                for (int i = 0; i < MENU.size(); i++)
                    served.add(sequence.guest(round * MENU.size() + i).request());
                assertEquals(new HashSet<>(MENU), served, "seed " + seed + " round " + round);
            }
            for (int i = 1; i < 10 * MENU.size(); i++)
                assertNotEquals(
                        sequence.guest(i - 1).request(),
                        sequence.guest(i).request(),
                        "seed " + seed);
        }
    }

    @Test
    void vipGuestsFollowTheIntervalAndZeroDisablesThem() {
        FeastOrderSequence everyFifth = new FeastOrderSequence(MENU, 1L, 5);
        FeastOrderSequence none = new FeastOrderSequence(MENU, 1L, 0);
        assertEquals(
                List.of(4, 9, 14, 19),
                IntStream.range(0, 20).filter(i -> everyFifth.guest(i).vip()).boxed().toList());
        assertTrue(IntStream.range(0, 50).noneMatch(i -> none.guest(i).vip()));
    }

    @Test
    void specialGuestsAreRareOrderOnlyTheirOwnDishesAndAreNeverVip() {
        FeastOrderSequence sequence =
                new FeastOrderSequence(MENU, 3L, 5, List.of(FOX), 5, List.of());
        List<FeastGuest> guests = IntStream.range(0, 4000).mapToObj(sequence::guest).toList();
        long specials = guests.stream().filter(FeastGuest::isSpecial).count();
        assertTrue(specials > 120 && specials < 280, "about 5% special guests: " + specials);
        for (FeastGuest guest : guests) {
            if (guest.isSpecial()) {
                assertEquals(FOX, guest.profile());
                assertTrue(FOX.requests().contains(guest.request()));
                assertFalse(guest.vip());
            } else assertTrue(MENU.contains(guest.request()));
        }
        FeastOrderSequence off = new FeastOrderSequence(MENU, 3L, 5, List.of(FOX), 0, List.of());
        assertTrue(IntStream.range(0, 500).noneMatch(i -> off.guest(i).isSpecial()));
        FeastOrderSequence noGuests = new FeastOrderSequence(MENU, 3L, 5, List.of(), 50, List.of());
        assertTrue(IntStream.range(0, 500).noneMatch(i -> noGuests.guest(i).isSpecial()));
    }

    @Test
    void namedVipsUseOnlyTheirMenuAndKeepTheMapBonusAndDeliveryDeck() {
        var chart = new FeastRequest("chart", "地图", Material.MAP, 1, 200, "cartographer", "taiga");
        var navigator = new FeastGuestProfile("navigator", "领航员", "VILLAGER", 0, List.of(chart));
        var plain = new FeastOrderSequence(MENU, 16L, 2);
        var sequence = new FeastOrderSequence(MENU, 16L, 2, List.of(), 0, List.of(navigator));
        for (int i = 0; i < 40; i++) {
            var guest = sequence.guest(i);
            if (i % 2 == 1) {
                assertTrue(guest.vip());
                assertFalse(guest.isSpecial());
                assertEquals(navigator, guest.profile());
                assertEquals(chart, guest.request());
                assertEquals(300, guest.basePoints(50));
            } else assertTrue(MENU.contains(guest.request()));
            assertEquals(plain.delivery(i), sequence.delivery(i));
        }
    }

    @Test
    void bonusesApplyToTheBasePointsOnly() {
        var bread = MENU.getFirst();
        assertEquals(100, new FeastGuest(1, bread, false, null).basePoints(50));
        assertEquals(150, new FeastGuest(1, bread, true, null).basePoints(50));
        assertEquals(360, new FeastGuest(1, FOX.requests().getFirst(), false, FOX).basePoints(10));
        assertThrows(IllegalArgumentException.class, () -> new FeastGuest(1, bread, true, FOX));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new FeastOrderSequence(List.of(), 1, 5));
        assertThrows(IllegalArgumentException.class, () -> new FeastOrderSequence(MENU, 1, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FeastOrderSequence(MENU, 1, 5, List.of(FOX), 101, List.of()));
        assertThrows(
                IllegalArgumentException.class, () -> new FeastOrderSequence(MENU, 1, 5).guest(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FeastGuestProfile("x", "x", "ZOMBIE", 0, FOX.requests()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FeastGuestProfile("x", "x", "FOX", 0, List.of()));
    }
}

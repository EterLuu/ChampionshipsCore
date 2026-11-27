package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import static org.junit.jupiter.api.Assertions.*;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class SliderMapIdsTest {
    @Test
    void allTilesRetainTheirIdsOnReuseAndRestart() {
        var next = new AtomicInteger(17);
        var first = SliderMapIds.resolve(List.of(), id -> false, next::getAndIncrement);
        assertEquals(1024, new HashSet<>(first).size());
        var loaded =
                SliderMapIds.resolve(first, first::contains, () -> fail("allocated on restart"));
        for (var hall : SliderHall.values())
            for (int round = 1; round <= 2; round++)
                for (int i = 0; i < 128; i++) {
                    int slot = SliderMapIds.index(hall, round, i);
                    assertEquals(17 + slot, loaded.get(slot));
                }
    }

    @Test
    void repairsOnlyMissingInvalidOrDuplicateSlots() {
        var next = new AtomicInteger();
        List<Object> stored =
                new ArrayList<>(SliderMapIds.resolve(List.of(), n -> false, next::getAndIncrement));
        stored.set(11, stored.get(9));
        stored.set(20, "bad");
        stored.set(21, null);
        stored.removeLast();
        var fixed = SliderMapIds.resolve(stored, id -> id != 77, next::getAndIncrement);
        assertEquals(1024, new HashSet<>(fixed).size());
        assertEquals(1029, next.get());
        for (int i = 0; i < 1024; i++)
            assertEquals(Set.of(11, 20, 21, 77, 1023).contains(i), fixed.get(i) >= 1024);
    }

    @Test
    void filledProfileMatchesItsContourAtBothEndsOfTravel() {
        var fills = new HashSet<List<SliderShape.Rect>>();
        for (var shape : SliderShape.STYLES) {
            assertTrue(fills.add(shape.fill));
            for (var edge : shape.outline) {
                double x = edge.x() + edge.w() / 2, y = edge.y() + edge.h() / 2;
                double dx = edge.w() < edge.h() ? SliderHall.OUTLINE : 0;
                double dy = edge.h() < edge.w() ? SliderHall.OUTLINE : 0;
                assertNotEquals(
                        inside(shape, x - dx, y - dy),
                        inside(shape, x + dx, y + dy),
                        "Only the exterior of the fill should be stroked");
            }
            for (int col : new int[] {0, 22})
                for (int row : new int[] {1, 7})
                    for (var r : shape.outline) {
                        assertTrue(
                                SliderHall.coreLeft(col) + r.x() >= 0
                                        && SliderHall.coreLeft(col) + r.x() + r.w() <= 16);
                        assertTrue(
                                SliderHall.coreTop(row) + r.y() >= 0
                                        && SliderHall.coreTop(row) + r.y() + r.h() <= 8);
                    }
        }
    }

    private static boolean inside(SliderShape shape, double x, double y) {
        return shape.fill.stream()
                .anyMatch(r -> r.x() <= x && x < r.x() + r.w() && r.y() <= y && y < r.y() + r.h());
    }
}

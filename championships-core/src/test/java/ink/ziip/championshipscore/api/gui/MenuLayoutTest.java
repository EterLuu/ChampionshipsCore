package ink.ziip.championshipscore.api.gui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class MenuLayoutTest {
    @Test
    void sparseReorderedSlotsVisitEveryEntryExactlyOnceAndRejectControls() {
        var layout = new MenuLayout(54, List.of(20, 3, 40, 6), List.of(20, 45), Map.of("next", 40));
        assertEquals(List.of(20, 3, 6), layout.content());
        assertEquals(List.of(45), layout.border());
        var visited = new ArrayList<Integer>();
        for (int page = 0; page < layout.pageCount(10); page++) {
            for (int slot : layout.content()) {
                int index = layout.index(page, slot);
                if (index < 10) visited.add(index);
            }
            assertEquals(-1, layout.index(page, 40));
            assertEquals(-1, layout.index(page, 45));
            assertEquals(-1, layout.index(page, -999));
        }
        assertEquals(MenuLayout.range(0, 10), visited);
        assertEquals(1, layout.pageCount(0));
        assertEquals(3, layout.clampPage(999, 10));
        assertEquals(0, layout.clampPage(-1, 10));
    }

    @Test
    void sourceMutationCannotRemapAnOpenInventory() {
        var slots = new ArrayList<>(List.of(2, 4));
        var controls = new java.util.HashMap<>(Map.of("next", 26));
        var layout = new MenuLayout(27, slots, List.of(), controls);
        slots.clear();
        controls.put("next", 8);
        assertEquals(List.of(2, 4), layout.content());
        assertEquals(26, layout.control("next"));
        assertThrows(UnsupportedOperationException.class, () -> layout.content().add(5));
    }

    @Test
    void centeredPlacementCannotOverwriteMovedControlsOrMutateAfterOpening() {
        var row = new ArrayList<>(List.of(4));
        var placements =
                new java.util.HashMap<Integer, List<Integer>>(Map.of(1, row, 3, List.of(2, 4, 6)));
        var layout =
                new MenuLayout(27, List.of(2, 4, 6), List.of(3, 4), Map.of("back", 4), placements);
        assertEquals(List.of(2, 6), layout.content());
        assertEquals(List.of(2), layout.pageSlots(1, 3));
        assertEquals(2, layout.index(1, 2, 3));
        assertEquals(-1, layout.index(1, 4, 3));
        row.set(0, 8);
        placements.clear();
        assertEquals(List.of(4), layout.arrangements().get(1));
        assertEquals(List.of(3), layout.border());
        assertThrows(IllegalStateException.class, () -> layout.index(0, 2));
    }

    @Test
    void unusableLayoutCannotSilentlyLoseEntries() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MenuLayout(27, List.of(27), List.of(), Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MenuLayout(27, List.of(2, 2), List.of(), Map.of()));
        assertThrows(
                IllegalStateException.class,
                () -> new MenuLayout(27, List.of(2), List.of(), Map.of("back", 2)).pageCount(10));
    }
}

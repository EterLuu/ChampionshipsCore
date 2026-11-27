package ink.ziip.championshipscore.api.game.iamnotarobot.model;

import ink.ziip.championshipscore.api.game.iamnotarobot.geometry.SliderHall;

import java.util.*;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;

public final class SliderMapIds {
    public static final int TILES = SliderHall.MAP_ROWS * SliderHall.MAP_COLUMNS;
    public static final int COUNT = SliderHall.values().length * 2 * TILES;

    private SliderMapIds() {}

    public static int index(SliderHall station, int round, int tile) {
        return (2 * station.ordinal() + round - 1) * TILES + tile;
    }

    /** Keep slot positions even when an individual saved entry is malformed or missing. */
    public static List<Integer> resolve(List<?> stored, IntPredicate exists, IntSupplier allocate) {
        List<Integer> ids = new ArrayList<>(COUNT);
        Set<Integer> used = new HashSet<>();
        for (int slot = 0; slot < COUNT; slot++) {
            Object value = slot < stored.size() ? stored.get(slot) : null;
            int id = resolveSlot(value, used, exists, allocate);
            ids.add(id);
        }
        return ids;
    }

    public static int resolveSlot(
            Object value, Set<Integer> used, IntPredicate exists, IntSupplier allocate) {
        int id = value instanceof Integer n ? n : -1;
        if (id < 0 || !exists.test(id) || !used.add(id)) {
            id = allocate.getAsInt();
            if (!used.add(id)) throw new IllegalStateException("Duplicate allocated map " + id);
        }
        return id;
    }
}

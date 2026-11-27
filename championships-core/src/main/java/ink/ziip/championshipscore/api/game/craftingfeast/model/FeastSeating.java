package ink.ziip.championshipscore.api.game.craftingfeast.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntToDoubleFunction;
import java.util.random.RandomGenerator;

/** Picks a random free seat; VIPs try the private rooms first, everyone else the main hall. */
public final class FeastSeating {
    private FeastSeating() {}

    /**
     * Teaching guests use a nearby free seat, retaining the normal hall/private-room preference.
     */
    public static int nearest(
            List<Boolean> privateSeats,
            Set<Integer> unavailable,
            boolean prefersPrivate,
            IntToDoubleFunction distance) {
        for (boolean wantPrivate : new boolean[] {prefersPrivate, !prefersPrivate}) {
            int nearest = -1;
            double best = Double.POSITIVE_INFINITY;
            for (int seat = 0; seat < privateSeats.size(); seat++) {
                if (privateSeats.get(seat) != wantPrivate || unavailable.contains(seat)) continue;
                double value = distance.applyAsDouble(seat);
                if (value < best) {
                    best = value;
                    nearest = seat;
                }
            }
            if (nearest >= 0) return nearest;
        }
        return -1;
    }

    /**
     * @param privateSeats one flag per seat index, true for private-room chairs
     * @param unavailable occupied seats and seats whose dishes are not cleared yet
     * @return the chosen seat index, or -1 when every seat is taken
     */
    public static int choose(
            List<Boolean> privateSeats,
            Set<Integer> unavailable,
            boolean prefersPrivate,
            RandomGenerator random) {
        for (boolean wantPrivate : new boolean[] {prefersPrivate, !prefersPrivate}) {
            List<Integer> free = new ArrayList<>();
            for (int seat = 0; seat < privateSeats.size(); seat++)
                if (privateSeats.get(seat) == wantPrivate && !unavailable.contains(seat))
                    free.add(seat);
            if (!free.isEmpty()) return free.get(random.nextInt(free.size()));
        }
        return -1;
    }
}

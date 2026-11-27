package ink.ziip.championshipscore.api.game.craftingfeast.geometry;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

class FeastRoutesTest {
    @Test
    void customersWalkToTheBlockTwoBehindTheirChairInEveryDirection() {
        Location counter = new Location(null, 21.5, 64, 32.5);
        Location entrance = new Location(null, 21.5, 64, 3.5);
        // yaw -> expected approach block offset (behind the chair)
        Object[][] cases = {{90f, 2, 0}, {-90f, -2, 0}, {0f, 0, -2}, {180f, 0, 2}};
        for (Object[] c : cases) {
            Location seat = new Location(null, 7.5, 64.5, 30.5, (float) c[0], 0);
            Location original = seat.clone();
            Location approach = FeastRoutes.seating(counter, seat).getFirst();
            assertEquals(7.5 + (int) c[1], approach.getX(), 1e-9, "yaw " + c[0]);
            assertEquals(30.5 + (int) c[2], approach.getZ(), 1e-9, "yaw " + c[0]);
            assertEquals(64, approach.getY());
            List<Location> leaving = FeastRoutes.leaving(entrance, seat);
            assertEquals(List.of(approach, entrance), leaving);
            assertEquals(original, seat, "Routes must not mutate configured seats");
        }
    }

    @Test
    void routesTranslateWithTheRestaurantCopy() {
        Location counter = new Location(null, 21.5, 64, 32.5);
        Location seat = new Location(null, 14.5, 64.5, 14.5, 90, 0);
        var first = FeastRoutes.seating(counter, seat);
        var copy = FeastRoutes.seating(counter.clone().add(448, 0, 0), seat.clone().add(448, 0, 0));
        assertEquals(first.stream().map(p -> p.clone().add(448, 0, 0)).toList(), copy);
    }
}
